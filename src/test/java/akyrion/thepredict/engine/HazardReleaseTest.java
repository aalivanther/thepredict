package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.DisplacementOrigin;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HazardOutcome;
import akyrion.thepredict.domain.HitShape;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PressureLevers;
import akyrion.thepredict.domain.ReactionMask;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ZoneCastOrder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An accepted throw enters the hazard book from finishCast and from a released tell (M24.10). */
class HazardReleaseTest {
    @Test
    void aReleasedTellOfAnAcceptedThrowIsAttributedWithinSixtyTicks() {
        var harness = harness();
        harness.session.openTell(0, 10L, 8, 0, 0, false, harness.target);
        assertEquals(ReleaseOutcome.RELEASED, harness.control.releaseTell(harness.session, 10L));
        var recent = harness.ledger.attribute(harness.target, 4, false, 70L, 60, false).orElseThrow();
        assertEquals(HazardOutcome.PUSHED_INTO_HAZARD, recent.outcome());
        assertEquals(DisplacementKind.STRONG_PUSH, recent.kind());
        assertTrue(harness.ledger.attribute(harness.target, 4, false, 80L, 60, false).isEmpty());
    }

    @Test
    void aReleasedTellAttributesTheOpenTargetAfterLoseTarget() {
        var harness = harness();
        harness.session.openTell(0, 10L, 8, 0, 0, false, harness.target);
        harness.session.loseTarget();
        assertEquals(ReleaseOutcome.RELEASED, harness.control.releaseTell(harness.session, 10L));
        var recent = harness.ledger.attribute(harness.target, 4, false, 70L, 60, false).orElseThrow();
        assertEquals(HazardOutcome.PUSHED_INTO_HAZARD, recent.outcome());
        assertEquals(DisplacementKind.STRONG_PUSH, recent.kind());
        assertTrue(harness.ledger.attribute(harness.target, 4, false, 71L, 60, false).isEmpty());
    }

    @Test
    void aReleasedTellAttributesTheOpenTargetNotALaterRecordedTarget() {
        var harness = harness();
        var other = UUID.randomUUID();
        harness.session.openTell(0, 10L, 8, 0, 0, false, harness.target);
        harness.session.recordTarget(other, 4.0);
        assertEquals(ReleaseOutcome.RELEASED, harness.control.releaseTell(harness.session, 10L));
        var recent = harness.ledger.attribute(harness.target, 4, false, 70L, 60, false).orElseThrow();
        assertEquals(HazardOutcome.PUSHED_INTO_HAZARD, recent.outcome());
        assertEquals(DisplacementKind.STRONG_PUSH, recent.kind());
        assertTrue(harness.ledger.attribute(other, 4, false, 70L, 60, false).isEmpty());
        assertTrue(harness.ledger.attribute(harness.target, 4, false, 71L, 60, false).isEmpty());
    }

    @Test
    void anExhaustedDeferDoesNotEnterTheHazardBook() {
        var harness = harness();
        harness.gate.releaseAs(ReleaseOutcome.DEFERRED_INVULNERABLE);
        var other = UUID.randomUUID();
        harness.session.openTell(0, 10L, 8, 0, 0, false, harness.target);
        harness.session.loseTarget();
        harness.session.recordTarget(other, 4.0);
        assertEquals(ReleaseOutcome.RELEASED, harness.control.releaseTell(harness.session, 10L));
        assertTrue(harness.ledger.attribute(harness.target, 4, false, 70L, 60, false).isEmpty());
        assertTrue(harness.ledger.attribute(other, 4, false, 70L, 60, false).isEmpty());
    }

    @Test
    void finishCastOfAnAcceptedThrowIsAttributedWithinSixtyTicks() {
        var harness = harness();
        harness.control.useZone((session, choice, tick) -> ZoneCastOrder.Step.CAST);
        var intent = new TacticalIntent(harness.session.key(), new TacticId("hold_ground"), Movement.HOLD,
                Optional.of(harness.choice.id()), 0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE,
                false);
        harness.session.adoptIntent(intent);
        assertEquals(ExecutionOutcome.CAST, harness.control.attemptChoice(harness.session, intent, harness.choice,
                10L, false));
        var recent = harness.ledger.attribute(harness.target, 4, false, 70L, 60, false).orElseThrow();
        assertEquals(HazardOutcome.PUSHED_INTO_HAZARD, recent.outcome());
        assertEquals(DisplacementKind.STRONG_PUSH, recent.kind());
    }

    private static Harness harness() {
        var choice = choice();
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                Map.of(choice.id(), choice), base.contexts(), base.movement(), base.health(), base.threat(),
                base.baseAi(), base.timing());
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
        var target = UUID.randomUUID();
        session.recordTarget(target, 3.0);
        session.difficulty().levers(new PressureLevers(0, ReactionMask.ALL, 0, true, 0));
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var gate = new Gate();
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        var ledger = new HazardLedger();
        control.useHazard((mob, victim, kind, tick) -> ledger.note(victim, mob.entityId(), kind, tick));
        return new Harness(control, session, choice, target, ledger, gate);
    }

    private static SkillChoice choice() {
        var facts = new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false, false, true,
                OptionalInt.empty(), 0, 0, HitShape.UNKNOWN, DisplacementKind.STRONG_PUSH, DisplacementKind.NONE,
                DisplacementOrigin.READ);
        return SkillChoice.untyped(new ChoiceId("throw"), "Throw", Set.of(), true, 0).withNativeFacts(facts);
    }

    private record Harness(IntentControl control, MobSession session, SkillChoice choice, UUID target,
                           HazardLedger ledger, Gate gate) {
    }

    private static final class Gate implements CombatActions {
        private ReleaseOutcome outcome = ReleaseOutcome.RELEASED;

        void releaseAs(ReleaseOutcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return true;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }

        @Override
        public ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                      CastInterrupt interrupt) {
            return outcome;
        }
    }
}
