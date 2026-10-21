package akyrion.thepredict.adapter.server.events;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickGuardTest {
    @Test
    void aZeroGuardNeverNotesAndAnOverTickDoes() {
        assertThrows(NullPointerException.class, () -> new TickGuard(null, () -> 0, over -> {
        }));
        var notes = new AtomicInteger();
        var silent = new TickGuard(() -> 0, () -> 80, over -> notes.incrementAndGet());
        silent.onTickEnd(new ServerTickEndEvent(1, 80, -1L));
        assertEquals(0, notes.get());
        var guard = new TickGuard(() -> 40, () -> 10, over -> notes.addAndGet(over ? 1 : 10));
        guard.onTickEnd(new ServerTickEndEvent(2, 10, -5L));
        guard.onTickEnd(new ServerTickEndEvent(3, 10, 60_000_000_000L));
        var slow = new TickGuard(() -> 40, () -> 55, over -> notes.addAndGet(over ? 100 : 0));
        slow.onTickEnd(new ServerTickEndEvent(4, 55, 60_000_000_000L));
        assertEquals(111, notes.get());
    }
}
