package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.learn.CaptureDecision;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.ExploreChoice;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.engine.distill.DistillRuntime;
import akyrion.thepredict.engine.distill.TeachingBoard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Applies exploration and a value-of-information skip after the question plan and before the send (M28.5, M28.8).
 * An unarmed session leaves the plan unchanged. There is no command, timer or global switch.
 */
final class CaptureGate {
    private CaptureGate() {
    }

    /**
     * Drops questions of mobs that explore or skip, and holds a skip on the session.
     *
     * @param encounter the encounter
     * @param plan the planned questions
     * @param tick the server tick
     * @return the plan that may be sent
     */
    static EncounterQuestions.Plan apply(Encounter encounter, EncounterQuestions.Plan plan, long tick) {
        return apply(encounter, plan, tick, null, DecisionTap.NONE);
    }

    /**
     * Arms from the learning section, then drops or holds. Null settings leave a pre-armed session alone.
     *
     * @param encounter the encounter
     * @param plan the planned questions
     * @param tick the server tick
     * @param settings the learning section, or null
     * @param tap where {@code PLAYER_JOINED} is emitted
     * @return the plan that may be sent
     */
    static EncounterQuestions.Plan apply(Encounter encounter, EncounterQuestions.Plan plan, long tick,
                                         LearningSettings settings, DecisionTap tap) {
        return apply(encounter, plan, tick, settings, tap, null, TeachingBoard.NONE);
    }

    /**
     * Arms, then applies a distilled hold before the value-of-information skip. Null distillation is off.
     *
     * @param encounter the encounter
     * @param plan the planned questions
     * @param tick the server tick
     * @param settings the learning section, or null
     * @param tap where {@code PLAYER_JOINED} is emitted
     * @param distill the distill section, or null
     * @param teaching where a collected capture is noted, {@link TeachingBoard#NONE} without a board
     * @return the plan that may be sent
     */
    static EncounterQuestions.Plan apply(Encounter encounter, EncounterQuestions.Plan plan, long tick,
                                         LearningSettings settings, DecisionTap tap, DistillSettings distill,
                                         TeachingBoard teaching) {
        return apply(encounter, plan, tick, settings, tap, distill, teaching, DistillRuntime.EMPTY);
    }

    /**
     * Same as the teaching overload, reading a published runtime when one is installed.
     *
     * @param runtime coverage and policy, {@link DistillRuntime#EMPTY} without one
     * @return the plan that may be sent
     */
    static EncounterQuestions.Plan apply(Encounter encounter, EncounterQuestions.Plan plan, long tick,
                                         LearningSettings settings, DecisionTap tap, DistillSettings distill,
                                         TeachingBoard teaching, DistillRuntime runtime) {
        return apply(encounter, plan, tick, settings, tap, distill, teaching, runtime, 0L);
    }

    /**
     * Same as the runtime overload. {@code minute} is {@code ServerClock.nanoTime() / 60_000_000_000}.
     *
     * @param minute the minute of this capture
     * @return the plan that may be sent
     */
    static EncounterQuestions.Plan apply(Encounter encounter, EncounterQuestions.Plan plan, long tick,
                                         LearningSettings settings, DecisionTap tap, DistillSettings distill,
                                         TeachingBoard teaching, DistillRuntime runtime, long minute) {
        var drop = new HashSet<MobKey>();
        for (var session : encounter.memberSessions()) {
            visit(session, encounter, plan, tick, settings, tap, distill, teaching, runtime, minute, drop);
        }
        if (drop.isEmpty()) {
            return plan;
        }
        var questions = new ArrayList<QuestionSpec>();
        for (var question : plan.questions()) {
            if (!drop.contains(question.mob())) {
                questions.add(question);
            }
        }
        var profiles = new HashMap<MobKey, TacticalProfile>();
        for (var entry : plan.profiles().entrySet()) {
            if (!drop.contains(entry.getKey())) {
                profiles.put(entry.getKey(), entry.getValue());
            }
        }
        var checks = new ArrayList<FitCheck>();
        for (var check : plan.fitChecks()) {
            if (!drop.contains(check.mob())) {
                checks.add(check);
            }
        }
        return new EncounterQuestions.Plan(questions, profiles, checks, plan.prunes());
    }

    private static void visit(MobSession session, Encounter encounter, EncounterQuestions.Plan plan, long tick,
                              LearningSettings settings, DecisionTap tap, DistillSettings distill,
                              TeachingBoard teaching, DistillRuntime runtime, long minute, HashSet<MobKey> drop) {
        var learning = session.learning();
        learning.activateIfReady();
        if (settings == null) {
            keepJoined(learning);
        } else {
            CaptureArm.arm(session, encounter, plan, settings, tap);
        }
        var minWindows = (settings == null ? LearningSettings.DEFAULT : settings).minWindowsForValue();
        learning.noteCaptureLeader(VoiFill.leader(session, CaptureArm.options(session, plan), minWindows));
        var hold = DistillCapture.hold(session, encounter, plan, tick, distill, teaching, runtime, minute, tap,
                minWindows);
        var outcome = CaptureDecision.decide(learning.explore(), learning.voi(), tick, hold);
        outcome.explore().ifPresent(choice -> adopt(session, plan, choice));
        learning.noteWouldSkip(outcome.wouldSkip());
        outcome.held().ifPresent(learning::hold);
        if (outcome.dropQuestions() || distill != null && distill.suppressClient(learning.distillCovered())) {
            drop.add(session.key());
        }
        learning.noteCaptured(session.liveSignature());
    }

    private static void keepJoined(akyrion.thepredict.engine.learn.SessionLearning learning) {
        if (learning.playerJoined() && !learning.training()) {
            learning.armExplore(ExploreArm.NONE);
            learning.noteSkip(LearnCause.PLAYER_JOINED);
        }
    }

    private static void adopt(MobSession session, EncounterQuestions.Plan plan, ExploreChoice choice) {
        session.learning().noteExplore(choice);
        if (!choice.inStratum() || session.learning().explore().acceptedJev()) {
            return;
        }
        var options = CaptureArm.options(session, plan);
        session.learning().noteExploreOptions(options);
        if (choice.index() < 0 || choice.index() >= options.size()) {
            return;
        }
        session.noteResolvedTactic(options.get(choice.index()));
        session.learning().noteAdoption(true);
    }
}
