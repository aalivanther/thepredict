package akyrion.thepredict.engine;

import akyrion.thepredict.adapter.server.vocabulary.PredictorPatternCondition;
import akyrion.thepredict.adapter.server.vocabulary.PredictorPlaceholders;
import akyrion.thepredict.adapter.server.vocabulary.PredictorZoneCondition;
import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.domain.ZoneMode;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DAL-01: the board shows the group, the patterns and the zone of the session, and a republish keeps them. */
class StanceBoardTest {
    private static final ServerClock CLOCK = ServerClock.of(() -> 0L, () -> 0L);

    @Test
    void aMeleeTokenRoleAndPlanReachTheBoardAndSurviveAStanceChange() {
        var board = new IntentBoard();
        var session = controlled(board);
        session.groupCopy().publish(1, 1, GroupRole.PRESSURE, 3, 4, GroupPlan.SURROUND_TARGET.ordinal(), 1, 1,
                GroupMode.COORDINATE);
        StanceBoard.sync(board, session);
        var view = board.current(session.key().entityId());
        assertEquals(1, view.tokenMask());
        assertEquals("melee", PredictorPlaceholders.format(PredictorPlaceholders.Field.TOKEN, view, 0L, 0L));
        assertEquals(Optional.of(GroupRole.PRESSURE), view.role());
        assertEquals(Optional.of(GroupPlan.SURROUND_TARGET), view.plan());
        assertEquals(4, view.squadSize());
        assertEquals(3, view.slotIndex());
        session.stanceState(StanceState.withAction(StanceState.INITIAL, ActionStance.RECOVERY.ordinal()));
        StanceBoard.sync(board, session);
        var later = board.current(session.key().entityId());
        assertTrue(later != view, "the stance change republished the view");
        assertEquals(1, later.tokenMask(), "the token stays published after the stance change");
        assertEquals(Optional.of(GroupRole.PRESSURE), later.role());
    }

    @Test
    void theTickLayerVisitPublishesAGroupChangeWithinTheTick() {
        var board = new IntentBoard();
        var session = controlled(board);
        session.groupCopy().publish(1, 1, GroupRole.FLANK, 0, 2, -1, 2, 1, GroupMode.COORDINATE);
        TickLayer.visit(List.of(session), 8, 0, 5L, 1_000L, board, new QuietReader(Map.of(), true), NO_STEPS,
                new DecisionMetrics(() -> 0L), ignored -> {
                }, 600, 4, 0, 0L, () -> 0L, ignored -> {
                });
        var view = board.current(session.key().entityId());
        assertEquals(2, view.tokenMask(), "the visit of the tick layer republishes the copy the group step wrote");
        assertEquals(Optional.of(GroupRole.FLANK), view.role());
    }

    @Test
    void aSharedSyncPublishesAGroupChangeAndLeavesTheIntentAsItWas() {
        var board = new IntentBoard();
        var session = controlled(board);
        StanceBoard.syncShared(board, session);
        assertEquals(0, board.current(session.key().entityId()).tokenMask());
        session.groupCopy().publish(1, 1, GroupRole.FLANK, 0, 2, -1, 2, 1, GroupMode.COORDINATE);
        StanceBoard.syncShared(board, session);
        var view = board.current(session.key().entityId());
        assertEquals(2, view.tokenMask());
        assertEquals(Optional.of(GroupRole.FLANK), view.role());
        assertEquals(Optional.empty(), view.plan());
        assertEquals(ControlLevel.JEV, view.level(), "the shared fields leave the intent as it was");
    }

    @Test
    void patternsReachTheBoardOnlyInBiasAndFull() {
        var board = new IntentBoard();
        var session = controlled(board);
        var mask = EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit();
        session.publishPattern(mask, EnemyAction.STRAFE_LEFT.ordinal());
        session.patternMode(PatternMode.OBSERVE);
        StanceBoard.syncShared(board, session);
        var condition = new PredictorPatternCondition("predictable_strafe_left", board, CLOCK);
        assertEquals(0, board.current(session.key().entityId()).targetPatternMask());
        assertFalse(condition.matches(board.current(session.key().entityId())));
        session.patternMode(PatternMode.BIAS);
        StanceBoard.syncShared(board, session);
        var view = board.current(session.key().entityId());
        assertEquals(mask, view.targetPatternMask());
        assertEquals(EnemyAction.STRAFE_LEFT.ordinal(), view.forecast());
        assertTrue(condition.matches(view));
    }

    @Test
    void zoneFactsAndThePushUnderTheMobReachTheBoard() {
        var board = new IntentBoard();
        var session = controlled(board);
        session.zoneReading(ZoneMode.FULL, PushDanger.NONE, Feasibility.FEASIBLE);
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(), Set.of(),
                Set.of()).withZone(Set.of(ZoneFact.EDGE_BEHIND, ZoneFact.TARGET_NEAR_DROP), false, 0L), 0L);
        session.tickSignals(TickSignals.pack(TickSignals.PRESENT, 0, 0, 0, 0, 100, 100,
                PushDanger.LETHAL.ordinal(), 0, 0, 0));
        StanceBoard.syncShared(board, session);
        var view = board.current(session.key().entityId());
        assertTrue(new PredictorZoneCondition("edge_behind", board, CLOCK).matches(view));
        assertEquals(1 << ZoneFact.EDGE_BEHIND.ordinal(), view.zoneFacts(), "an engine-only fact is not published");
        assertEquals("lethal", PredictorPlaceholders.format(PredictorPlaceholders.Field.PUSHDANGER, view, 0L, 0L));
    }

    @Test
    void anotherGenerationIsNotCarriedIntoTheRepublishedIntent() {
        var board = new IntentBoard();
        var session = new MobSession(new MobKey(UUID.randomUUID(), 1), EngineFixtures.profile("Guardian"));
        var stale = IntentView.base(0).withPressure(5).withGroup(GroupRole.PRESSURE.ordinal(), -1, 1, 2, 0);
        board.publish(new MobKey(session.key().entityId(), 0), stale);
        session.adoptIntent(intent(session));
        StanceBoard.sync(board, session);
        var view = board.current(session.key().entityId());
        assertEquals(1, view.generation());
        assertEquals(-1, view.pressure(), "a rung of the old generation is not shown");
        assertEquals(0, view.tokenMask(), "a token of the old generation is not shown");
    }

    @Test
    void anUnchangedSessionOutsideAnyGroupAllocatesNothingOnTheVisitCheck() {
        var board = new IntentBoard();
        var session = controlled(board);
        StanceBoard.syncShared(board, session);
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (var warm = 0; warm < 20_000; warm++) {
            StanceBoard.syncShared(board, session);
        }
        var thread = Thread.currentThread().threadId();
        var before = threads.getThreadAllocatedBytes(thread);
        for (var round = 0; round < 10_000; round++) {
            StanceBoard.syncShared(board, session);
        }
        var used = threads.getThreadAllocatedBytes(thread) - before;
        assertTrue(used < 4_096, "allocated " + used + " bytes");
    }

    /** Steps that do nothing: the visit only reads and publishes. */
    private static final ExecutionSteps NO_STEPS = new ExecutionSteps() {
        @Override
        public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
        }

        @Override
        public void react(MobSession session, MobProbe probe, long tick, long nowNanos) {
        }

        @Override
        public void evaluated(Encounter encounter, long tick, long nowNanos) {
        }
    };

    private static MobSession controlled(IntentBoard board) {
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        session.useBoard(board);
        var intent = intent(session);
        session.adoptIntent(intent);
        board.publish(session.key(), intent.toView());
        return session;
    }

    private static TacticalIntent intent(MobSession session) {
        return new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD, Optional.empty(), 0.9,
                List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
    }
}
