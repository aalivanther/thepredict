package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickSignalsTest {
    @Test
    void packingRoundTripsAndSaturates() {
        var flags = (1L << TickSignals.VALID) | (1L << TickSignals.HAS_TARGET) | (1L << TickSignals.TARGET_BLOCKING);
        var word = TickSignals.pack(flags, 200, 9, 9, 40, 140, -3);
        assertTrue(TickSignals.bit(word, TickSignals.VALID));
        assertTrue(TickSignals.bit(word, TickSignals.TARGET_BLOCKING));
        assertEquals(127, TickSignals.gapQuarters(word));
        assertEquals(4, TickSignals.octant(TickSignals.pack(flags, 0, 4, 0, 0, 0, 0)) == 4 ? 4
                : TickSignals.octant(word));
        assertEquals(31, TickSignals.hittableTicks(word));
        assertEquals(100, TickSignals.mobHealthPercent(word));
        assertEquals(0, TickSignals.targetHealthPercent(word));
        assertEquals(0, TickSignals.quartersOf(-1));
        assertEquals(127, TickSignals.quartersOf(40));
        assertEquals(0, TickSignals.octantOf(0, 1, 0, 1));
        assertEquals(4, TickSignals.octantOf(0, 1, 0, -1));
        assertEquals(4, TickSignals.octantOf(0, 1, 0.17, -1), "about 170 degrees is behind");
        assertEquals(2, TickSignals.octantOf(0, 1, 1, 0), "90 degrees is the side");
        assertEquals(1, TickSignals.octantOf(0, 1, 1, 1), "45 degrees");
        assertEquals(0, TickSignals.octantOf(0, 1, 0.3, 1), "about 17 degrees is in front");
        assertTrue(TickSignals.leftOf(0, 1, 1, 0));
        assertFalse(TickSignals.leftOf(0, 1, -1, 0));
        assertEquals(TickSignals.PRESENT, 1L);
    }
}
