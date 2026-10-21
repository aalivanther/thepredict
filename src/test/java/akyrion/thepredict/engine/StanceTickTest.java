package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.StanceMachine;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tick layer calls the stance machine only when a deadline is due (M23.1). */
class StanceTickTest {
    @Test
    void aQuietTickDoesNotStepAndABreakRewritesBusyUntil() {
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        session.nextStanceTick(50);
        var steps = new AtomicInteger();
        var execution = new ExecutionSteps() {
            @Override
            public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
            }

            @Override
            public void react(MobSession found, akyrion.thepredict.domain.MobProbe probe, long tick, long nowNanos) {
            }

            @Override
            public void evaluated(Encounter encounter, long tick, long nowNanos) {
            }

            @Override
            public void advanceStance(MobSession found, long tick) {
                steps.incrementAndGet();
            }
        };
        TickLayer.visit(java.util.List.of(session), 4, 0, 10, 10, new IntentBoard(), reader(), execution,
                new DecisionMetrics(() -> 0L), found -> {
                }, 600, 4, 0, 0L, () -> 0L, found -> {
                });
        assertEquals(0, steps.get());
        long start = 100;
        var out = new long[5];
        StanceMachine.step(out, session.stanceState(), start + 5, start, Long.MAX_VALUE, 0, 0, false, true, false, 2,
                20, StanceMachine.EVENT_POISE, PoiseSettings.defaults().staggerTicks(), 0);
        assertEquals(ActionStance.STAGGERED, ActionStance.values()[akyrion.thepredict.domain.StanceState.action(
                out[StanceMachine.OUT_STATE])]);
        assertEquals(start + 35, out[StanceMachine.OUT_BUSY]);
    }

    @Test
    void aRecoveryThatEndsOnTheReactiveCycleClearsTheStanceDeadline() {
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        session.stanceState(akyrion.thepredict.domain.StanceState.withAction(
                akyrion.thepredict.domain.StanceState.INITIAL, ActionStance.RECOVERY.ordinal()));
        session.actionUntilTick(100L);
        session.nextStanceTick(100L);
        StanceClock.evaluateBase(session, 105L, QuietActions.INSTANCE, new long[5]);
        assertEquals(ActionStance.NONE, session.actionStance());
        assertEquals(Long.MAX_VALUE, session.nextStanceTick(), "the lane would otherwise step the session every tick");
    }

    @Test
    void aStepWithoutActionClearsTheDeadlineAndAnEarlyStepMovesItToTheAction() {
        var idle = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        idle.nextStanceTick(50L);
        StanceClock.advance(idle, 60L, QuietActions.INSTANCE, new long[5]);
        assertEquals(Long.MAX_VALUE, idle.nextStanceTick());
        var winding = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        winding.stanceState(akyrion.thepredict.domain.StanceState.withAction(
                akyrion.thepredict.domain.StanceState.INITIAL, ActionStance.WINDUP.ordinal()));
        winding.actionUntilTick(80L);
        winding.nextStanceTick(60L);
        StanceClock.advance(winding, 60L, QuietActions.INSTANCE, new long[5]);
        assertEquals(ActionStance.WINDUP, winding.actionStance());
        assertEquals(80L, winding.nextStanceTick());
    }

    private static CombatReader reader() {
        return new CombatReader() {
            @Override
            public java.util.Optional<akyrion.thepredict.domain.CombatFacts> capture(
                    akyrion.thepredict.domain.EncounterId encounter, java.util.List<MobKey> members,
                    akyrion.thepredict.domain.CaptureContext context) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<akyrion.thepredict.domain.MobProbe> probe(MobKey mob) {
                return java.util.Optional.empty();
            }

            @Override
            public boolean inCombat(UUID entityId) {
                return false;
            }

            @Override
            public boolean withinFollowRange(MobKey mob, MobKey member) {
                return false;
            }

            @Override
            public boolean isActive(UUID entityId) {
                return true;
            }

            @Override
            public java.util.Optional<akyrion.thepredict.domain.ThreatTableState> threatTable(MobKey mob) {
                return java.util.Optional.empty();
            }
        };
    }
}
