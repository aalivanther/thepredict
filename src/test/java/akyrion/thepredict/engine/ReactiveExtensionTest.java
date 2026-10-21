package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reactive deadlines move inside the saved capture, never onto a later one, and a choice casts once per epoch. */
class ReactiveExtensionTest {
    private static final long NANOS = IntentPolicy.NANOS_PER_TICK;
    private static final TacticId PRESS = new TacticId("press");
    private static final ChoiceId STRIKE = new ChoiceId("strike");

    @Test
    void aDeadlineInsideTwoTicksMovesForwardAndStopsAtTheCaptureCap() {
        var harness = harness(400, 40);
        var early = arm(harness, 10 * NANOS, 10 * NANOS + NANOS, 0, ControlLevel.JEV, false);
        harness.control.extend(early, 10 * NANOS);
        assertEquals(50 * NANOS, early.intent().orElseThrow().expiresAtNanos());
        assertEquals(ControlLevel.JEV, early.intent().orElseThrow().level());
        assertEquals(0, early.intent().orElseThrow().startTick());
        assertEquals(false, early.intent().orElseThrow().skillConsumed());
        assertEquals(MicroBehaviour.ORBIT, harness.sessions.board.current(early.key().entityId()).micro());
        assertEquals(MicroSide.RIGHT, harness.sessions.board.current(early.key().entityId()).microSide());
        assertEquals(1, harness.metrics.layer(DecisionMetrics.LayerCounter.INTENT_EXTENDED));
        assertEquals(0, harness.tap.events);
        var capped = arm(harness, 370 * NANOS, 371 * NANOS, 0, ControlLevel.JEV, false);
        harness.control.extend(capped, 370 * NANOS);
        assertEquals(400 * NANOS, capped.intent().orElseThrow().expiresAtNanos());
        assertTrue(capped.intent().orElseThrow().expiresAtNanos() < 370 * NANOS + 40 * NANOS);
    }

    @Test
    void twoTicksOfMarginDoesNotExtend() {
        var harness = harness(400, 40);
        var session = arm(harness, 10 * NANOS, 12 * NANOS, 0, ControlLevel.JEV, false);
        harness.control.extend(session, 10 * NANOS);
        assertEquals(12 * NANOS, session.intent().orElseThrow().expiresAtNanos());
        assertEquals(0, harness.metrics.layer(DecisionMetrics.LayerCounter.INTENT_EXTENDED));
    }

    @Test
    void anOddTickDeadlineIsExtendedOnThePreviousCycle() {
        var harness = harness(400, 40);
        var session = arm(harness, 4 * NANOS, 5 * NANOS, 0, ControlLevel.JEV, false);
        harness.control.extend(session, 4 * NANOS);
        var kept = session.intent().orElseThrow();
        assertEquals(ControlLevel.JEV, kept.level());
        assertTrue(kept.expiresAtNanos() > 5 * NANOS);
        assertEquals(0, kept.startTick());
        assertEquals(false, kept.skillConsumed());
    }

    @Test
    void aLaterCaptureDoesNotCutAnExtensionWhileTheLiveSignatureMatches() {
        var harness = harness(400, 40);
        var session = arm(harness, 10 * NANOS, 11 * NANOS, 17, ControlLevel.JEV, false);
        session.noteCaptureSignature(99);
        harness.control.extend(session, 10 * NANOS);
        assertEquals(50 * NANOS, session.intent().orElseThrow().expiresAtNanos());
        assertNotEquals(session.intentCaptureSignature(), session.captureSignature());
        session.noteLiveSignature(99);
        harness.control.extend(session, 49 * NANOS);
        assertEquals(50 * NANOS, session.intent().orElseThrow().expiresAtNanos());
        assertEquals(1, harness.metrics.layer(DecisionMetrics.LayerCounter.INTENT_EXTENDED));
    }

    @Test
    void alternativeLocalAndDebugIntentsDoNotExtend() {
        var harness = harness(400, 40);
        for (var level : List.of(ControlLevel.ALTERNATIVE, ControlLevel.LOCAL)) {
            var session = arm(harness, NANOS, 2 * NANOS, 3, level, false);
            harness.control.extend(session, NANOS);
            assertEquals(2 * NANOS, session.intent().orElseThrow().expiresAtNanos());
        }
        var debug = arm(harness, NANOS, 2 * NANOS, 3, ControlLevel.JEV, true);
        harness.control.extend(debug, NANOS);
        assertEquals(2 * NANOS, debug.intent().orElseThrow().expiresAtNanos());
        assertEquals(0, harness.metrics.layer(DecisionMetrics.LayerCounter.INTENT_EXTENDED));
    }

    @Test
    void twoHundredTicksOfExtensionCastTheChoiceOnce() {
        var harness = harness(400, 40);
        var session = harness.sessions.registry.open(EngineFixtures.id(1), profile(harness.timing));
        var target = EngineFixtures.player(1);
        session.recordTarget(target, 6);
        session.tickSignals(TickSignals.pack(flag(TickSignals.VALID) | flag(TickSignals.HAS_TARGET), 24, 0, 0, 0,
                100, 100));
        session.facts(seen(session));
        var probe = new MobProbe(session.key(), EngineFixtures.WORLD, HealthBand.HEALTHY, Optional.of(target),
                Optional.of(DistanceBand.RANGED));
        var saved = LiveSignature.of(session, probe);
        session.adoptIntent(intent(session, NANOS, ControlLevel.JEV));
        session.holdIntentCapture(saved, 0);
        session.noteCaptureSignature(saved ^ 1);
        harness.sessions.board.publish(session.key(), session.intent().orElseThrow().toView()
                .withMicro(MicroBehaviour.ORBIT, MicroSide.LEFT));
        var layer = layer(harness);
        for (var tick = 0; tick < 200; tick++) {
            var now = tick * NANOS;
            if (tick % 2 == 0) {
                layer.react(session, probe, tick, now);
            }
            harness.control.castIfReady(session, tick, now);
        }
        var kept = session.intent().orElseThrow();
        assertEquals(List.of(STRIKE.value()), harness.gate.casts);
        assertEquals(0, session.choiceEpoch());
        assertTrue(harness.metrics.layer(DecisionMetrics.LayerCounter.INTENT_EXTENDED) >= 4);
        assertEquals(ControlLevel.JEV, kept.level());
        assertEquals(0, kept.startTick());
        assertEquals(0, harness.tap.events);
        assertNotEquals(session.intentCaptureSignature(), session.captureSignature());
        assertEquals(session.intentCaptureSignature(), session.liveSignature());
    }

    private static ReactiveLayer layer(Harness harness) {
        var budget = new RequestBudget(ExecutionFixture.settings(2000));
        var tracker = new ServiceStatusTracker(new ServiceCheck(ServiceState.READY, "listed"), 1_000_000L,
                () -> 0.5, line -> {
                });
        var fallback = new FallbackStep(tracker, harness.sessions.encounters, budget, harness.control,
                new ExecutionSettings(1_000_000_000L, 3, "jev-1.13.0", false));
        return new ReactiveLayer(harness.sessions.encounters, harness.sessions.agenda, harness.control, fallback);
    }

    private static MobSession arm(Harness harness, long now, long expires, int signature, ControlLevel level,
                                  boolean debug) {
        var session = harness.sessions.registry.open(EngineFixtures.id(harness.nextMob++), profile(harness.timing));
        session.adoptIntent(intent(session, expires, level));
        session.holdIntentCapture(signature, 0);
        session.noteLiveSignature(signature);
        session.noteCaptureSignature(signature ^ 1);
        harness.sessions.board.publish(session.key(), session.intent().orElseThrow().toView()
                .withMicro(MicroBehaviour.ORBIT, MicroSide.RIGHT));
        session.debugIntent(debug);
        return session;
    }

    private static TacticalIntent intent(MobSession session, long expires, ControlLevel level) {
        return new TacticalIntent(session.key(), PRESS, Movement.APPROACH, Optional.of(STRIKE), 0.9, List.of(),
                Map.of(), level, 0, expires, false);
    }

    private static MobFacts seen(MobSession session) {
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1), DistanceBand.RANGED,
                Trend.STEADY, true, true, true, false, false);
        return new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(PRESS),
                Set.of(), Set.of());
    }

    private static Harness harness(int maximum, int horizon) {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var tap = new CountingTap();
        var gate = new Gate();
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics), tap);
        return new Harness(sessions, metrics, tap, gate, control, timing(maximum, horizon));
    }

    private static DecisionTiming timing(int maximum, int horizon) {
        return new DecisionTiming(20, 20, maximum, 0.5, 0.2, 1).withCasting(CastMode.INTENT_ONLY, 0)
                .withLayer(TickLayerMode.REACTIONS, ReactionScope.ALL, Set.of(), 0, DecisionCadence.REACTIVE, 6,
                        horizon);
    }

    private static TacticalProfile profile(DecisionTiming timing) {
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        var strike = SkillChoice.typed(STRIKE, "TPV_Strike", Set.of(), true, 0, purpose);
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 50)),
                Map.of(STRIKE, strike), List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
    }

    private static long flag(int bit) {
        return 1L << bit;
    }

    private static final class Harness {
        final EngineFixtures.Sessions sessions;
        final DecisionMetrics metrics;
        final CountingTap tap;
        final Gate gate;
        final IntentControl control;
        final DecisionTiming timing;
        int nextMob = 1;

        Harness(EngineFixtures.Sessions sessions, DecisionMetrics metrics, CountingTap tap, Gate gate,
                IntentControl control, DecisionTiming timing) {
            this.sessions = sessions;
            this.metrics = metrics;
            this.tap = tap;
            this.gate = gate;
            this.control = control;
            this.timing = timing;
        }
    }

    private static final class CountingTap implements DecisionTap {
        int events;

        @Override
        public TapDetail detail() {
            return TapDetail.FULL;
        }

        @Override
        public void intentEvent(MobKey mob, DecisionTap.IntentEvent event) {
            events++;
        }
    }

    private static final class Gate implements CombatActions {
        final List<String> casts = new ArrayList<>();

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            casts.add(choice.id().value());
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return casts.isEmpty();
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, java.util.UUID playerId, double amount) {
        }
    }
}
