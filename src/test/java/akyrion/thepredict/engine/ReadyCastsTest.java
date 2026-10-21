package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.UntypedCastMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ready casts of M19.4 and M19.5 executed by the engine: cadence, fairness, charges, a captured charge, epochs and a
 * debug intent that keeps every other candidate quiet.
 */
class ReadyCastsTest {
    private static final TacticId PRESS = new TacticId("press_attack");
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId BOLT = new ChoiceId("bolt");
    private static final ChoiceId SWING = new ChoiceId("swing");

    @Test
    void oneCastPerTickWhenThereIsNoCooldownAndNoWindUp() {
        var ticks = castTicks(bench(CastMode.ANY_READY, pastWindows(strike(0)), pastWindows(bolt())), 100);
        assertEquals(100, ticks.size(), ticks.toString());
        for (var index = 1; index < ticks.size(); index++) {
            assertEquals(1L, ticks.get(index) - ticks.get(index - 1), ticks.toString());
        }
        var windowed = castTicks(bench(CastMode.ANY_READY, strike(0), bolt()), 100);
        assertEquals(List.of(0L, 10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L), windowed,
                "two hits the window takes land one window apart: one more inside it would deal nothing (D-EF2 b)");
    }

    private static List<Long> castTicks(Bench bench, int until) {
        var ticks = new ArrayList<Long>();
        for (var tick = 0; tick < until; tick++) {
            var before = bench.actions.casts.size();
            bench.fire(tick);
            if (bench.actions.casts.size() > before) {
                ticks.add((long) tick);
            }
        }
        return ticks;
    }

    @Test
    void aWindUpBlocksASecondStartUntilItExpires() {
        var bench = bench(CastMode.ANY_READY, strike(8), pastWindows(bolt()));
        bench.fire(0);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        for (var tick = 1; tick < 8; tick++) {
            bench.fire(tick);
            bench.control.cast(bench.session, tick, 0);
            assertEquals(1, bench.actions.casts.size(), "tick " + tick);
            assertTrue(tick < bench.session.busyUntilTick());
        }
        bench.fire(8);
        assertEquals(2, bench.actions.casts.size());
    }

    @Test
    void aChangedTargetDuringWindUpDoesNotWaitForTheWindUpOrCallMythic() {
        var bench = bench(CastMode.ANY_READY, strike(6), pastWindows(bolt()));
        bench.fire(0);
        bench.actions.liveMatch = false;
        bench.fire(1);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertEquals(2, bench.session.nextReadyTick());
        assertEquals(0, bench.metrics.total(DecisionMetrics.ReadyCounter.BUSY_BLOCKED));
        assertTrue(bench.session.consumeContextMiss());
    }

    @Test
    void aChangedAiTargetDuringWindUpIsNotEligibleOnTheNextTick() {
        var within = strike(6).withNativeFacts(new NativeSkillFacts(OptionalDouble.empty(), 1,
                NativeSkillFacts.SEQUENTIAL, OptionalInt.empty(), OptionalDouble.of(4), OptionalDouble.empty(),
                OptionalDouble.empty(), false, false, false, false));
        var bench = bench(CastMode.ANY_READY, within, pastWindows(bolt()));
        bench.fire(0);
        bench.actions.aiMatch = false;
        bench.fire(1);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertEquals(2, bench.session.nextReadyTick());
        assertTrue(bench.session.consumeContextMiss());
    }

    @Test
    void anIneligibleOrShieldedIntentChoiceTriesTheNextCandidateAndKeepsTheTactic() {
        var missed = bench(CastMode.ANY_READY, strike(0), bolt());
        missed.actions.script.add(ExecutionOutcome.NOT_ELIGIBLE);
        missed.fire(0);
        assertEquals(List.of(STRIKE.value(), BOLT.value()), missed.actions.casts);
        assertEquals(PRESS, missed.session.intent().orElseThrow().tactic());
        assertFalse(missed.session.intent().orElseThrow().skillConsumed());
        var shielded = bench(CastMode.ANY_READY, strike(0), bolt());
        shielded.actions.script.add(ExecutionOutcome.SKIPPED_SHIELD);
        shielded.fire(0);
        assertEquals(List.of(STRIKE.value(), BOLT.value()), shielded.actions.casts);
        assertEquals(PRESS, shielded.session.intent().orElseThrow().tactic());
        assertFalse(shielded.session.intent().orElseThrow().skillConsumed());
    }

    @Test
    void oneHundredDueSessionsWithACapOfSixtyFourAreAllSeenWithinTwoTicks() {
        var harness = new Harness();
        var profile = profile(CastMode.INTENT_ONLY, strike(0));
        var opened = new ArrayList<MobSession>(100);
        for (var index = 1; index <= 100; index++) {
            var session = harness.sessions.registry.open(EngineFixtures.id(index), profile);
            arm(session, profile);
            opened.add(session);
        }
        assertEquals(64, harness.upkeep.readyCasts(64, 0, 0));
        assertEquals(36, harness.metrics.total(DecisionMetrics.ReadyCounter.READY_DEFERRED));
        assertEquals(64, harness.gate.casters.size());
        harness.upkeep.readyCasts(64, 1, 0);
        assertEquals(100, harness.gate.casters.size());
        for (var session : opened) {
            assertTrue(harness.gate.casters.contains(session.key().entityId()), session.key().toString());
        }
    }

    @Test
    void threeParallelChargesCastOnceEachThenOncePerRecharge() {
        var bench = bench(CastMode.INTENT_ONLY, strike(0).withNativeFacts(new NativeSkillFacts(OptionalDouble.of(0.5),
                3, "PARALLEL", OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), false, false, false, false)));
        bench.actions.parallel = true;
        bench.actions.recharge = 10;
        for (var tick = 0L; tick <= 12; tick++) {
            bench.actions.tick = tick;
            if (bench.session.nextReadyTick() <= tick) {
                bench.fire(tick);
            }
        }
        assertEquals(List.of(0L, 1L, 2L, 10L, 11L, 12L), bench.actions.castTicks);
    }

    @Test
    void anExternalChargeInsideTwoTicksIsCastAndAFarCachedTickIsNotReread() {
        var near = bench(CastMode.ANY_READY, strike(0));
        near.actions.wait = 1;
        near.session.scheduleReady(500);
        near.control.recalibrate(near.session, 0);
        assertEquals(500, near.session.nextReadyTick());
        var reads = near.actions.reads;
        assertTrue(reads > 0);
        near.actions.wait = 0;
        near.actions.tick = 1;
        near.control.recalibrate(near.session, 1);
        assertTrue(near.actions.reads > reads);
        assertEquals(1, near.session.nextReadyTick());
        near.fire(1);
        assertEquals(List.of(STRIKE.value()), near.actions.casts);

        var far = bench(CastMode.ANY_READY, strike(0));
        far.actions.wait = 40;
        far.session.scheduleReady(500);
        far.control.recalibrate(far.session, 0);
        var held = far.actions.reads;
        far.actions.wait = 0;
        far.control.recalibrate(far.session, 1);
        assertEquals(held, far.actions.reads);
        assertEquals(500, far.session.nextReadyTick());
        assertTrue(far.actions.casts.isEmpty());
    }

    /**
     * SA-13: MythicMobs counts the cooldown in wall milliseconds. At 10 TPS it runs out twice as fast as the ticks, so
     * the far tick read on the first cycle lies 20 ticks after the real end. Each reactive cycle reads it again and the
     * cast starts within one cycle of the first tick {@code ready} answers true.
     */
    @Test
    void aFarCachedTickIsReadAgainEachCycleSoALowTpsCooldownCastsOnTime() {
        var slow = bench(CastMode.ANY_READY, strike(0));
        slow.session.scheduleReady(500);
        var readyAt = -1L;
        for (long tick = 0; tick <= 60 && slow.actions.castTicks.isEmpty(); tick++) {
            slow.actions.tick = tick;
            slow.actions.wait = (int) Math.max(0, 40 - 2 * tick);
            if (readyAt < 0 && slow.actions.ready(slow.session.key(), slow.session.listedChoices()[0])) {
                readyAt = tick;
            }
            if (tick % ReactiveLayer.CYCLE_TICKS == 0) {
                slow.control.recalibrate(slow.session, tick);
            }
            if (slow.session.nextReadyTick() <= tick) {
                slow.fire(tick);
            }
        }
        assertEquals(20, readyAt);
        assertFalse(slow.actions.castTicks.isEmpty());
        var castAt = slow.actions.castTicks.getFirst();
        assertTrue(castAt >= readyAt && castAt - readyAt <= ReactiveLayer.CYCLE_TICKS,
                "the cast waited for the far tick read on the first cycle: cast at " + castAt);
    }

    @Test
    void noEpochCastsTwiceAcrossTenThousandTicks() {
        for (var seed : new long[] {1L, 7L, 99L}) {
            var bench = bench(CastMode.INTENT_ONLY, strike(0));
            bench.actions.noise = new Random(seed);
            var perEpoch = new HashMap<Integer, Integer>();
            for (var tick = 0; tick < 10_000; tick++) {
                var before = bench.actions.castTicks.size();
                bench.fire(tick);
                bench.fire(tick);
                var started = bench.actions.castTicks.size() - before;
                assertTrue(started <= 1, "seed " + seed + " tick " + tick);
                if (started == 1) {
                    perEpoch.merge(bench.session.choiceEpoch(), 1, Integer::sum);
                }
            }
            assertTrue(perEpoch.values().stream().allMatch(count -> count == 1), perEpoch.toString());
            assertTrue(perEpoch.size() > 1000, perEpoch.size() + " epochs");
        }
    }

    @Test
    void aDebugIntentCastsOnlyItsChoice() {
        var bench = bench(CastMode.ANY_READY, strike(0), bolt());
        bench.control.applyDebug(bench.session, bench.session.intent().orElseThrow(), 0);
        for (var tick = 0; tick < 40; tick++) {
            bench.fire(tick);
        }
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
    }

    private static void arm(MobSession session, TacticalProfile profile) {
        var choice = profile.choices().keySet().iterator().next();
        session.adoptIntent(new TacticalIntent(session.key(), PRESS, Movement.APPROACH, Optional.of(choice), 0.9,
                List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE / 2, false));
        var eligible = new LinkedHashSet<>(profile.tactics().keySet());
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), eligible, Set.of(),
                Set.of(), Map.of(), eligible));
        session.recordTarget(EngineFixtures.player(1), 2.0);
        session.scheduleReady(0);
    }

    private static Bench bench(CastMode mode, SkillChoice... choices) {
        return new Bench(mode, choices);
    }

    private static SkillChoice strike(int windUp) {
        return SkillChoice.typed(STRIKE, "TPV_Strike", Set.of(), true, 0, purpose(windUp));
    }

    private static SkillChoice bolt() {
        return SkillChoice.typed(BOLT, "TPV_Bolt", Set.of(), true, 0, purpose(0));
    }

    /**
     * The same choice with a damage that ignores invulnerability (a native fact): its hit neither waits for the own
     * window of the mob nor holds another hit, so a test of the cadence keeps its subject now that a profile of
     * {@code TickLayer: casts} also waits for its own hits (D-EF2 b).
     */
    private static SkillChoice pastWindows(SkillChoice choice) {
        return choice.withNativeFacts(new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL,
                OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false,
                true, false, false));
    }

    private static SkillPurpose purpose(int windUp) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(2.5),
                OptionalDouble.empty(), windUp, CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
    }

    private static TacticalProfile profile(CastMode mode, SkillChoice... choices) {
        var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.DEFAULT, false, mode, 0,
                UntypedCastMode.TACTIC_ONLY);
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(choices[0].id()), Optional.empty(), 50));
        var declared = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var choice : choices) {
            declared.put(choice.id(), choice);
        }
        declared.put(SWING, new SkillChoice(SWING, ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(), true, 0));
        return new TacticalProfile("ready_ai", "Guardian", "Defeat the target", tactics, declared, List.of(),
                MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                List.of(), timing);
    }

    private static final class Bench {
        final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
        final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
        final Gate actions = new Gate();
        final IntentControl control;
        final MobSession session;

        Bench(CastMode mode, SkillChoice... choices) {
            control = new IntentControl(sessions.board, actions, metrics, new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                    DecisionTap.NONE);
            var declared = profile(mode, choices);
            session = sessions.registry.open(EngineFixtures.id(1), declared);
            arm(session, declared);
        }

        void fire(long tick) {
            actions.tick = tick;
            control.castIfReady(session, tick, 0);
        }
    }

    private static final class Harness {
        final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
        final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
        final Gate gate = new Gate();
        final IntentControl control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        final SessionUpkeep upkeep = new SessionUpkeep(sessions.registry, sessions.encounters, sessions.agenda,
                sessions.board, new ContextSignals(), new CoordinatorFixture.Reader(), new Idle(), () -> 1L,
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
        public void react(MobSession session, MobProbe probe, long tick, long nowNanos) {
        }

        @Override
        public void evaluated(Encounter encounter, long tick, long nowNanos) {
        }

        @Override
        public void castReady(MobSession session, long tick, long nowNanos) {
            control.castIfReady(session, tick, nowNanos);
        }
    }

    private static final class Idle implements MobControl {
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
        final List<String> casts = new ArrayList<>();
        final List<Long> castTicks = new ArrayList<>();
        final Set<UUID> casters = new LinkedHashSet<>();
        final ArrayDeque<ExecutionOutcome> script = new ArrayDeque<>();
        final long[] freeAt = new long[] {0, 0, 0};
        Random noise;
        long tick;
        int wait = -1;
        int reads;
        int recharge = 10;
        boolean parallel;
        boolean liveMatch = true;
        boolean aiMatch = true;

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            if (!script.isEmpty()) {
                var outcome = script.poll();
                if (outcome != ExecutionOutcome.CAST) {
                    casts.add(choice.id().value());
                    return outcome;
                }
            }
            if (noise != null && noise.nextInt(10) < 3) {
                return ExecutionOutcome.NOT_ELIGIBLE;
            }
            if (parallel) {
                var slot = freeSlot();
                if (slot < 0) {
                    return ExecutionOutcome.NOT_ELIGIBLE;
                }
                freeAt[slot] = tick + recharge;
            }
            casts.add(choice.id().value());
            castTicks.add(tick);
            casters.add(mob.entityId());
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            if (parallel) {
                return freeSlot() >= 0;
            }
            return wait < 0 || wait == 0;
        }

        @Override
        public OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
            reads++;
            if (parallel) {
                var slot = freeSlot();
                if (slot >= 0) {
                    return OptionalInt.of(0);
                }
                var soon = Long.MAX_VALUE;
                for (var at : freeAt) {
                    soon = Math.min(soon, at);
                }
                return OptionalInt.of((int) Math.max(0, soon - tick));
            }
            if (wait >= 0) {
                return OptionalInt.of(wait);
            }
            return OptionalInt.of(0);
        }

        @Override
        public boolean liveTargetMatches(MobKey mob, UUID expectedTarget) {
            return liveMatch;
        }

        @Override
        public boolean aiTargetMatches(MobKey mob, UUID expectedTarget) {
            return aiMatch;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }

        private int freeSlot() {
            for (var index = 0; index < freeAt.length; index++) {
                if (freeAt[index] <= tick) {
                    return index;
                }
            }
            return -1;
        }
    }
}
