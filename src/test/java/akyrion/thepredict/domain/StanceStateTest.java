package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Packed stance bits (M23.1). Bits 11–63 stay zero. */
class StanceStateTest {
    @Test
    void bitsRoundTripAndTheHighHalfStaysZero() {
        var state = StanceState.pend(StanceState.withAction(StanceState.INITIAL, ActionStance.WINDUP.ordinal()),
                BaseStance.GUARDED.ordinal(), 2);
        state = StanceState.withEnraged(state, true);
        assertEquals(BaseStance.NEUTRAL.ordinal(), StanceState.base(state));
        assertEquals(BaseStance.GUARDED.ordinal(), StanceState.pendingBase(state));
        assertEquals(ActionStance.WINDUP.ordinal(), StanceState.action(state));
        assertTrue(StanceState.pending(state));
        assertTrue(StanceState.enraged(state));
        assertEquals(2, StanceState.cycles(state));
        assertEquals(CombatStance.WINDUP, StanceState.visible(state));
        assertEquals(0L, state >>> 11);
        state = StanceState.applyBase(state, BaseStance.PRESSING.ordinal());
        assertEquals(BaseStance.PRESSING.ordinal(), StanceState.base(state));
        assertFalse(StanceState.pending(state));
        assertEquals(CombatStance.WINDUP, StanceState.visible(state));
    }
}
