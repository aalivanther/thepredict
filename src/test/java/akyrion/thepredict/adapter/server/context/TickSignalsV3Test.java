package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.TickSignals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickSignalsV3Test {
    @Test
    void theFourNewBitsAreDistinctFromBlocking() {
        assertEquals(52, TickSignals.TARGET_AIRBORNE);
        assertEquals(53, TickSignals.TARGET_SPRINTING);
        assertEquals(54, TickSignals.TARGET_RAISING);
        assertEquals(55, TickSignals.TARGET_FALLING_FAST);
        var raising = 1L << TickSignals.TARGET_RAISING;
        assertTrue(TickSignals.bit(raising, TickSignals.TARGET_RAISING));
        assertFalse(TickSignals.bit(raising, TickSignals.TARGET_BLOCKING));
    }
}
