package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickRingTest {
    @Test
    void theCursorWrapsAndCanResume() {
        var ring = new TickRing();
        assertEquals(0, ring.next(3));
        assertEquals(1, ring.next(3));
        ring.resume(2);
        assertEquals(2, ring.next(3));
        assertEquals(0, ring.next(3));
    }
}
