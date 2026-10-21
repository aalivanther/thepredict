package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure stance transitions (M23.1). */
class StanceMachineTest {
    @Test
    void aBreakAtFiveWithThirtyTicksEndsAtThirtyFiveNotFortyEight() {
        var out = new long[5];
        long start = 100L;
        StanceMachine.step(out, StanceState.INITIAL, start + 5, start, Long.MAX_VALUE, BaseStance.NEUTRAL.ordinal(),
                StanceCause.TACTIC.ordinal(), false, true, false, 2, 20, StanceMachine.EVENT_POISE, 30, 0);
        assertEquals(StanceCause.POISE_BREAK.ordinal(), out[StanceMachine.OUT_CAUSE]);
        assertEquals(start + 35, out[StanceMachine.OUT_BUSY]);
        assertEquals(ActionStance.STAGGERED.ordinal(), StanceState.action(out[StanceMachine.OUT_STATE]));
        assertTrue(out[StanceMachine.OUT_BUSY] != start + 48);
    }

    @Test
    void aTellIsNeverNoneWhileBusyAndAFeintClosesWithoutRecovery() {
        var out = new long[5];
        StanceMachine.step(out, StanceState.INITIAL, 10, 0, Long.MAX_VALUE, BaseStance.NEUTRAL.ordinal(),
                StanceCause.TACTIC.ordinal(), false, true, false, 2, 20, StanceMachine.EVENT_TELEGRAPH, 24, 20);
        assertEquals(ActionStance.WINDUP.ordinal(), StanceState.action(out[StanceMachine.OUT_STATE]));
        assertEquals(10 + 24 + 20, out[StanceMachine.OUT_BUSY]);
        assertTrue(out[StanceMachine.OUT_BUSY] > 10);
        StanceMachine.step(out, out[StanceMachine.OUT_STATE], 20, 0, out[StanceMachine.OUT_ACTION_UNTIL],
                BaseStance.NEUTRAL.ordinal(), StanceCause.TACTIC.ordinal(), false, true, false, 2, 20,
                StanceMachine.EVENT_FEINT, 0, 10);
        assertEquals(ActionStance.NONE.ordinal(), StanceState.action(out[StanceMachine.OUT_STATE]));
        assertEquals(StanceCause.WHIFF.ordinal(), out[StanceMachine.OUT_CAUSE]);
        assertEquals(20, out[StanceMachine.OUT_BUSY]);
    }

    @Test
    void hysteresisHoldsTheBaseAndSurvivalSkipsIt() {
        var out = new long[5];
        StanceMachine.step(out, StanceState.INITIAL, 1, 0, Long.MAX_VALUE, BaseStance.GUARDED.ordinal(),
                StanceCause.TACTIC.ordinal(), false, true, false, 2, 20, StanceMachine.EVENT_NONE, 0, 0);
        assertEquals(BaseStance.NEUTRAL.ordinal(), StanceState.base(out[StanceMachine.OUT_STATE]));
        assertTrue(StanceState.pending(out[StanceMachine.OUT_STATE]));
        StanceMachine.step(out, StanceState.INITIAL, 1, 0, Long.MAX_VALUE, BaseStance.EVASIVE.ordinal(),
                StanceCause.TACTIC.ordinal(), true, true, false, 2, 20, StanceMachine.EVENT_NONE, 0, 0);
        assertEquals(BaseStance.EVASIVE.ordinal(), StanceState.base(out[StanceMachine.OUT_STATE]));
        assertEquals(StanceCause.SURVIVAL.ordinal(), out[StanceMachine.OUT_CAUSE]);
    }
}
