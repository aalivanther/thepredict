package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ExploitPolicy;
import akyrion.thepredict.domain.PatternExploit;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.distill.DistillHold;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.LabelRule;
import akyrion.thepredict.engine.distill.DistillPatternShift;
import akyrion.thepredict.engine.distill.DistillRuntime;
import akyrion.thepredict.engine.distill.DistillStep;
import akyrion.thepredict.engine.distill.DistillTrace;
import akyrion.thepredict.engine.distill.PatternFactsNote;
import akyrion.thepredict.engine.distill.TeachingBoard;

import java.util.ArrayList;

/**
 * Distilled hold of one mob at capture (M29.4). The student decides only in a covered cell.
 */
public final class DistillCapture {
    private DistillCapture() {
    }

    /**
     * Decides the hold and stores the trace, the regime and the agreement mark.
     *
     * @param session the member
     * @param encounter the encounter
     * @param plan the planned questions
     * @param tick the server tick
     * @param distill the section, or null
     * @param teaching the board, {@link TeachingBoard#NONE} without one
     * @param runtime the runtime, {@link DistillRuntime#EMPTY} without one
     * @param minute minute from the server clock
     * @param tap the tap
     * @param minWindows windows a value estimate needs to be usable ({@code learning.minWindowsForValue})
     * @return the hold
     */
    public static DistillHold hold(MobSession session, Encounter encounter, EncounterQuestions.Plan plan, long tick,
                                   DistillSettings distill, TeachingBoard teaching, DistillRuntime runtime,
                                   long minute, DecisionTap tap, int minWindows) {
        var learning = session.learning();
        java.util.Objects.requireNonNull(teaching, "teaching");
        java.util.Objects.requireNonNull(runtime, "runtime");
        var mode = mode(session, encounter, distill);
        noteTeaching(session, plan, distill, teaching, minWindows);
        if (distill != null && mode != DistillMode.OFF) {
            RegimeCapture.consider(session, encounter, tick, distill, tap);
            learning.distillSession().cusumH(distill.cusumH());
        }
        if (mode == DistillMode.OFF) {
            return DistillHold.NONE;
        }
        var read = read(session, plan, distill, runtime, minute);
        var held = decide(session, encounter, plan, tick, distill, mode, read);
        learning.distillSession().trace(trace(session, distill, runtime, read, held));
        if (held.delegate()) {
            adopt(session, plan, read, held, tick, tap);
        }
        mark(session, plan, mode, read, held, minWindows);
        return held;
    }

    /** The step of this capture, with the audit sample and the tactic noted on the distill session. */
    private static DistillHold decide(MobSession session, Encounter encounter, EncounterQuestions.Plan plan, long tick,
                                      DistillSettings distill, DistillMode mode, DistillRuntime.View read) {
        var learning = session.learning();
        var state = learning.distillSession();
        var request = new DistillStep.Request(mode, read.covered(), read.ood(), read.source(), read.tactic(),
                learning.distillSeed(), state.samples(), distill == null ? learning.auditEvery() : distill.auditEvery(),
                learning.captures() == 0, group(session, encounter, plan), session.liveSignature(), tick,
                session.profile().timing().maximumIntentTicks());
        var held = DistillStep.consider(request);
        if (held.audit() || held.delegate()) {
            state.noteSample();
        }
        if (!read.tactic().isEmpty()) {
            state.noteTactic(read.tactic());
        }
        return held;
    }

    /**
     * The effective mode of this capture: the section, lowered by the verdict installed on the session and by the
     * profile's {@code Distill.Mode}; the bench withholds the student while a player is the live target or a
     * participant of the last capture (A16, LD-06), and the governor has the last word.
     */
    private static DistillMode mode(MobSession session, Encounter encounter, DistillSettings distill) {
        var learning = session.learning();
        var mode = distill == null ? learning.distillMode() : distill.mode();
        if (learning.distillMode() != DistillMode.OFF) {
            mode = mode.lower(learning.distillMode());
        }
        var profileMode = session.profile().distillMode();
        if (profileMode.isPresent()) {
            mode = mode.lower(profileMode.get());
        }
        var player = session.playerTarget() || CaptureArm.players(encounter);
        if (distill != null && distill.benchWithholds(player) && mode.rank() > DistillMode.SHADOW.rank()) {
            mode = DistillMode.SHADOW;
        }
        return learning.distillSession().governor().effective(mode, learning.distillSession().verdict() == DistillMode.OFF
                && learning.distillMode() != DistillMode.OFF ? learning.distillMode() : mode);
    }

    private static DistillRuntime.View read(MobSession session, EncounterQuestions.Plan plan, DistillSettings distill,
                                            DistillRuntime runtime, long minute) {
        var learning = session.learning();
        if (!runtime.published() || distill == null) {
            return new DistillRuntime.View(learning.distillCovered(), learning.distillOod(), learning.distillSource(),
                    learning.distillTactic(), "");
        }
        var names = names(session, plan);
        var key = WindowCredit.live(session);
        return runtime.view(key.text(), names, key.packed(), minute, distill.minCellDecisions(),
                distill.nonInferiorityMargin(), distill.coverageMaxAgeMinutes());
    }

    /**
     * Adopts the distilled hold. A live pattern may move the pick to another candidate only through the same gate as
     * the local pick ({@link ExploitPolicy#allowsNow} with the quota, the rest and the variety cap, A16), and only to a
     * candidate that counters an admitted pattern ({@link DistillPatternShift#prefer}, AE-08): the quota is spent only
     * when the pick really changes, and the pattern published is the one that candidate counters, read by ordinal (A7,
     * rule C16).
     */
    private static void adopt(MobSession session, EncounterQuestions.Plan plan, DistillRuntime.View read,
                              DistillHold held, long tick, DecisionTap tap) {
        var learning = session.learning();
        learning.noteDelegated();
        held.held().ifPresent(intent -> session.noteResolvedTactic(intent.tactic()));
        var plain = read.tactic();
        var admitted = session.admittedPatterns();
        if (admitted.isEmpty() || plain.isEmpty() || !exploitAllowed(session, tick)) {
            return;
        }
        var shift = DistillPatternShift.prefer(plain, names(session, plan), admitted, session.profile());
        if (shift.isEmpty()) {
            return;
        }
        session.noteResolvedTactic(new akyrion.thepredict.domain.TacticId(shift.get().tactic()));
        session.noteCastQuota((int) tick, true);
        learning.noteOpenPattern();
        tap.patternExploited(session.key(), new PatternExploit(shift.get().pattern(),
                akyrion.thepredict.domain.CounterBias.OPPORTUNITY, false, "distilled"));
        learning.noteLearned("distilled", akyrion.thepredict.engine.learn.LearnedResult.NEUTRAL);
    }

    private static boolean exploitAllowed(MobSession session, long tick) {
        var quota = session.exploitBudget().allows((int) tick, session.patternWindow(), session.patternQuota());
        return ExploitPolicy.allowsNow(session.patternMode(), session.patternResting(tick),
                session.learning().openPatterns(), session.patternVariety(), quota);
    }

    /**
     * Marks the agreement cell. A shadow comparison is measured only when both estimates are usable ({@code
     * minWindows} windows at least), so a cell without data never counts as a disagreement (LD-09).
     */
    private static void mark(MobSession session, EncounterQuestions.Plan plan, DistillMode mode,
                             DistillRuntime.View read, DistillHold held, int minWindows) {
        var learning = session.learning();
        var book = learning.distillSession().agreements();
        var regime = learning.regimeKey();
        var context = WindowCredit.live(session).text();
        if (mode == DistillMode.SHADOW && !read.tactic().isEmpty()) {
            var executed = names(session, plan);
            var executedName = executed.isEmpty() ? read.tactic() : executed.getFirst();
            var student = CellLookup.estimate(session, read.tactic(), minWindows);
            var other = CellLookup.estimate(session, executedName, minWindows);
            var measured = usable(student, minWindows) && usable(other, minWindows);
            book.prepareShadow("outcome", regime, context, (student.valueMillis() - other.valueMillis()) / 1000.0,
                    read.tactic().equals(executedName), measured);
        }
        if (held.audit()) {
            book.prepareAudit("outcome", regime, context);
        }
        if (held.delegate()) {
            book.prepareDelegated("outcome", regime, context);
        }
    }

    private static boolean usable(akyrion.thepredict.domain.learn.ValueEstimate estimate, int minWindows) {
        return !estimate.none() && !estimate.weak(minWindows);
    }

    private static DistillTrace trace(MobSession session, DistillSettings distill, DistillRuntime runtime,
                                     DistillRuntime.View read, DistillHold held) {
        var learning = session.learning();
        var cellN = 0;
        var lower = 0L;
        if (runtime.published()) {
            var cell = runtime.coverage().lookup(WindowCredit.live(session).text());
            if (cell.isPresent()) {
                cellN = cell.get().n();
                lower = Math.round(cell.get().lowerBound() * 1000.0);
            }
        }
        var regime = learning.distillSession().selectorOrNull() == null
                ? learning.regimeKey() : learning.distillSession().selectorOrNull().current().text();
        var facts = distill != null && distill.collect()
                ? PatternFactsNote.json(session.admittedPatterns()) : "";
        learning.distillSession().patternFactsJson(facts);
        var ood = read.ood().map(Enum::name).orElse("");
        return new DistillTrace(runtime.coverage().teacherModel(), regime, read.leaf(),
                read.tactic(), read.source().name(), read.covered(), ood, cellN, lower, held.audit(), held.delegate());
    }

    private static boolean group(MobSession session, Encounter encounter, EncounterQuestions.Plan plan) {
        if (session.learning().groupQuestion() || encounter.groupQuestion()) {
            return true;
        }
        for (var question : plan.questions()) {
            if (question.mob().equals(session.key()) && question.situation() != Situation.NOW) {
                return true;
            }
        }
        return false;
    }

    private static void noteTeaching(MobSession session, EncounterQuestions.Plan plan, DistillSettings distill,
                                     TeachingBoard teaching, int minWindows) {
        var learning = session.learning();
        var collect = distill == null ? learning.collectTeaching() : distill.collect();
        if (!collect || !teaching.keeps()) {
            return;
        }
        learning.distillSession().bind(teaching);
        var names = new ArrayList<String>();
        var estimates = new ArrayList<LabelRule.Estimate>();
        for (var tactic : CaptureArm.options(session, plan)) {
            names.add(tactic.value());
            var found = CellLookup.estimate(session, tactic.value(), minWindows);
            estimates.add(new LabelRule.Estimate(tactic.value(), found.valueMillis() / 1000.0,
                    found.sdMillis() / 1000.0, found.nEff()));
        }
        teaching.note(session.key().entityId().toString(), session.profile().id(), learning.regimeKey(),
                learning.contextText(), "outcome", names, estimates, learning.patternFacts(), learning.patternNone());
    }
    private static ArrayList<String> names(MobSession session, EncounterQuestions.Plan plan) {
        var names = new ArrayList<String>();
        for (var option : CaptureArm.options(session, plan)) {
            names.add(option.value());
        }
        return names;
    }
}
