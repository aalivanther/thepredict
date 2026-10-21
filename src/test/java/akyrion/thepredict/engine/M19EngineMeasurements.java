package akyrion.thepredict.engine;

import akyrion.thepredict.M19Measurements;
import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.UntypedCastMode;
import akyrion.thepredict.domain.UseCondition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/** Local measurements of the ready cast of M19.4 and M19.5. Run only from {@code M19Measurements}. */
public final class M19EngineMeasurements {
    private static final int WALK_WARMUP = 40;
    private static final int WALK_SAMPLES = 200;
    private static final int DUE_CAP = 64;

    private M19EngineMeasurements() {
    }

    /**
     * Writes {@code readyCasts}, {@code castIfReady}, the {@code ANY_READY} selection and {@code readyRecalibration}.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        var rows = new ArrayList<List<String>>();
        long idle200P99 = 0;
        for (var sessions : new int[] {20, 100, 200}) {
            var idle = walk(sessions, false, sessions);
            var due = walk(sessions, true, DUE_CAP);
            if (sessions == 200) {
                idle200P99 = M19Measurements.percentile(idle, 0.99);
            }
            rows.add(List.of(String.valueOf(sessions), String.valueOf(WALK_SAMPLES),
                    perSession(idle, sessions), M19Measurements.nanos(idle),
                    String.valueOf(Math.min(sessions, DUE_CAP)), M19Measurements.nanos(due)));
        }
        report.line("`readyCasts` walks every active session. A session that is not due is only compared. A due walk"
                + " checks at most 64 sessions (`decision.maxReadyChecksPerTick`) and each check runs `castIfReady` on"
                + " an `INTENT_ONLY` profile whose double cast returns `CAST`. Budget: ≤ 20 ns per session that is not"
                + " due; ≤ 30 µs p99 per tick with 20 mobs. The time wheel is not added: the not-due p99 of 200"
                + " sessions is the figure that would justify it, and only above 20 µs.");
        report.table(List.of("Sessions", "Samples", "Not due p50 / p99 / max per session",
                "Not due p50 / p99 / max per tick", "Due checks", "Due p50 / p99 / max per tick"), rows);
        report.line("Not-due p99 of 200 sessions: " + M19Measurements.micros(idle200P99)
                + " µs per tick. No time wheel.");
        castIfReady(report);
        anyReady(report);
        recalibrate(report);
    }

    private static long[] walk(int count, boolean due, int maximum) {
        var harness = new Harness();
        var opened = new ArrayList<MobSession>(count);
        var profile = due ? profile(CastMode.INTENT_ONLY) : null;
        for (var index = 1; index <= count; index++) {
            opened.add(due ? harness.sessions.registry.open(EngineFixtures.id(index), profile)
                    : harness.sessions.open(index));
        }
        if (due) {
            opened.forEach(M19EngineMeasurements::arm);
        }
        var nanos = new long[WALK_SAMPLES];
        for (var index = 0; index < WALK_WARMUP + WALK_SAMPLES; index++) {
            if (due) {
                for (var session : opened) {
                    session.adoptIntent(intent(session));
                    session.scheduleReady(index);
                    session.busyUntil(0);
                }
            }
            if (index < WALK_WARMUP) {
                harness.upkeep.readyCasts(maximum, index, 0);
                continue;
            }
            var started = System.nanoTime();
            M19Measurements.sink += harness.upkeep.readyCasts(maximum, index, 0);
            nanos[index - WALK_WARMUP] = System.nanoTime() - started;
        }
        return nanos;
    }

    private static void castIfReady(MeasurementReport report) {
        var harness = new Harness();
        var session = harness.sessions.registry.open(EngineFixtures.id(1), profile(CastMode.INTENT_ONLY));
        arm(session);
        var timed = new long[M19Measurements.microSamples()];
        for (var index = 0; index < M19Measurements.microWarmup() + timed.length; index++) {
            session.adoptIntent(intent(session));
            session.scheduleReady(index);
            session.busyUntil(0);
            if (index < M19Measurements.microWarmup()) {
                harness.control.castIfReady(session, index, 0);
                continue;
            }
            var started = System.nanoTime();
            harness.control.castIfReady(session, index, 0);
            timed[index - M19Measurements.microWarmup()] = System.nanoTime() - started;
        }
        var p50 = M19Measurements.percentile(timed, 0.50);
        var p99 = M19Measurements.percentile(timed, 0.99);
        report.line("`castIfReady` of one due `INTENT_ONLY` session. Budget: p50 ≤ 1.5 µs and p99 ≤ 5 µs, without the"
                + " MythicMobs registry. Measured p50 " + M19Measurements.micros(p50) + " µs, p99 "
                + M19Measurements.micros(p99) + " µs.");
        report.table(List.of("Row", "Samples", "p50 / p99 / max"), List.of(List.of("castIfReady",
                String.valueOf(timed.length), M19Measurements.nanos(timed))));
    }

    private static void anyReady(MeasurementReport report) {
        var harness = new Harness();
        harness.gate.cooling.add("skill0");
        var session = harness.sessions.registry.open(EngineFixtures.id(1), profile(CastMode.ANY_READY));
        arm(session);
        var nanos = new long[M19Measurements.microSamples()];
        for (var index = 0; index < M19Measurements.microWarmup() + nanos.length; index++) {
            session.scheduleReady(index);
            session.busyUntil(0);
            if (index < M19Measurements.microWarmup()) {
                harness.control.castIfReady(session, index, 0);
                continue;
            }
            var started = System.nanoTime();
            harness.control.castIfReady(session, index, 0);
            nanos[index - M19Measurements.microWarmup()] = System.nanoTime() - started;
        }
        var p50 = M19Measurements.percentile(nanos, 0.50);
        var p99 = M19Measurements.percentile(nanos, 0.99);
        report.line("`ANY_READY` selection inside `castIfReady`: the intent choice is cooling and seven other choices"
                + " are ordered, one of them needing a line of sight. Budget: p50 ≤ 2 µs and p99 ≤ 6 µs. Measured p50 "
                + M19Measurements.micros(p50) + " µs, p99 " + M19Measurements.micros(p99) + " µs.");
        report.table(List.of("Row", "Samples", "p50 / p99 / max"), List.of(List.of("ANY_READY selection",
                String.valueOf(nanos.length), M19Measurements.nanos(nanos))));
    }

    private static void recalibrate(MeasurementReport report) {
        var harness = new Harness();
        var profile = profile(CastMode.ANY_READY);
        var sessions = new ArrayList<MobSession>(20);
        for (var index = 1; index <= 20; index++) {
            var session = harness.sessions.registry.open(EngineFixtures.id(index), profile);
            arm(session);
            sessions.add(session);
        }
        var perRead = new long[M19Measurements.microSamples()];
        for (var index = 0; index < M19Measurements.microWarmup() + perRead.length; index++) {
            harness.gate.reads = 0;
            var session = sessions.getFirst();
            if (index < M19Measurements.microWarmup()) {
                harness.control.recalibrate(session, index);
                continue;
            }
            var started = System.nanoTime();
            harness.control.recalibrate(session, index);
            var elapsed = System.nanoTime() - started;
            perRead[index - M19Measurements.microWarmup()] = elapsed / Math.max(1, harness.gate.reads);
        }
        var tick = new long[WALK_SAMPLES];
        for (var index = 0; index < WALK_WARMUP + tick.length; index++) {
            if (index < WALK_WARMUP) {
                for (var session : sessions) {
                    harness.control.recalibrate(session, index);
                }
                continue;
            }
            var started = System.nanoTime();
            for (var session : sessions) {
                harness.control.recalibrate(session, index);
            }
            tick[index - WALK_WARMUP] = System.nanoTime() - started;
        }
        report.line("`readyRecalibration` reads `ticksUntilReady` of the intent choice and of each `ANY_READY`"
                + " candidate (8 choices). Budget: p50 ≤ 0.5 µs per read and p99 ≤ 60 µs per tick for 20 sessions."
                + " Measured p50 per read " + M19Measurements.micros(M19Measurements.percentile(perRead, 0.50))
                + " µs; p99 per tick " + M19Measurements.micros(M19Measurements.percentile(tick, 0.99)) + " µs.");
        report.table(List.of("Row", "p50 / p99 / max"), List.of(
                List.of("readyRecalibration per read", M19Measurements.nanos(perRead)),
                List.of("readyRecalibration 20 x 8 per tick", M19Measurements.nanos(tick))));
    }

    private static void arm(MobSession session) {
        session.adoptIntent(intent(session));
        var eligible = new LinkedHashSet<>(session.profile().tactics().keySet());
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), eligible, Set.of(),
                Set.of(), Map.of(), eligible));
        session.recordTarget(EngineFixtures.player(1), 2.0);
        session.scheduleReady(0);
    }

    private static TacticalIntent intent(MobSession session) {
        return new TacticalIntent(session.key(), new TacticId("cast0"), Movement.APPROACH,
                Optional.of(new ChoiceId("skill0")),
                0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE / 2, false);
    }

    private static TacticalProfile profile(CastMode mode) {
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        var tiers = new DamageTier[] {DamageTier.MEDIUM, DamageTier.HIGH, DamageTier.LOW, DamageTier.MEDIUM,
                DamageTier.HIGH, DamageTier.LOW, DamageTier.MEDIUM, DamageTier.LOW};
        for (var index = 0; index < 8; index++) {
            var choiceId = new ChoiceId("skill" + index);
            var risks = index == 1 ? Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT) : Set.<SkillRisk>of();
            var best = index == 3 ? Set.of(UseCondition.TARGET_IN_MELEE) : Set.<UseCondition>of();
            var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                    OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.SHORT, tiers[index], best, risks,
                    false, PurposeOrigin.SKILL);
            choices.put(choiceId, SkillChoice.typed(choiceId, "Skill" + index, Set.of(), true, 0, purpose));
            var tacticId = new TacticId("cast" + index);
            tactics.put(tacticId, new Tactic(tacticId, Movement.APPROACH, Optional.of(choiceId), Optional.empty(),
                    80 - index));
        }
        var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.DEFAULT, false, mode, 0,
                UntypedCastMode.TACTIC_ONLY);
        return new TacticalProfile("ready_ai", "Guardian", "Defeat the target", tactics, choices, List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
    }

    private static String perSession(long[] tickNanos, int sessions) {
        var per = new long[tickNanos.length];
        for (var index = 0; index < tickNanos.length; index++) {
            per[index] = tickNanos[index] / sessions;
        }
        return M19Measurements.nanos(per);
    }

    private static final class Harness {
        final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
        final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
        final Gate gate = new Gate();
        final IntentControl control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        final SessionUpkeep upkeep = new SessionUpkeep(sessions.registry, sessions.encounters, sessions.agenda,
                sessions.board, new ContextSignals(), new CoordinatorFixture.Reader(), new IdleControl(), () -> 1L,
                new ReadySteps(control), metrics);
    }

    private static final class ReadySteps implements ExecutionSteps {
        private final IntentControl control;

        ReadySteps(IntentControl control) {
            this.control = control;
        }

        @Override
        public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
        }

        @Override
        public void react(MobSession session, akyrion.thepredict.domain.MobProbe probe, long tick, long nowNanos) {
        }

        @Override
        public void evaluated(Encounter encounter, long tick, long nowNanos) {
        }

        @Override
        public void castReady(MobSession session, long tick, long nowNanos) {
            control.castIfReady(session, tick, nowNanos);
        }
    }

    private static final class IdleControl implements MobControl {
        @Override
        public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
            return new InstallResult.Installed(0);
        }

        @Override
        public MovementProgress progress(MobKey key) {
            return new MovementProgress(false, false, false, OptionalDouble.empty());
        }

        @Override
        public List<GoalSummary> describeGoals(MobKey key) {
            return List.of();
        }

        @Override
        public void release(MobKey key) {
        }
    }

    private static final class Gate implements CombatActions {
        final Set<String> cooling = new java.util.HashSet<>();
        int reads;

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return !cooling.contains(choice.id().value());
        }

        @Override
        public OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
            reads++;
            return OptionalInt.of(ready(mob, choice) ? 0 : 4);
        }

        @Override
        public boolean seesTarget(MobKey mob) {
            return true;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, java.util.UUID participantId, double amount) {
        }
    }
}
