package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerClockTest {
    @Test
    void ofDelegatesEachClockToItsSupplier() {
        var ticks = new AtomicLong(40);
        var nanos = new AtomicLong(-5);
        var clock = ServerClock.of(ticks::get, nanos::get);
        assertEquals(40, clock.currentTick());
        assertEquals(-5, clock.nanoTime());
        ticks.incrementAndGet();
        nanos.addAndGet(1_000);
        assertEquals(41, clock.currentTick());
        assertEquals(995, clock.nanoTime());
    }

    @Test
    void missingSuppliersAreRejected() {
        assertThrows(NullPointerException.class, () -> ServerClock.of(null, () -> 0L));
        assertThrows(NullPointerException.class, () -> ServerClock.of(() -> 0L, null));
    }
}
