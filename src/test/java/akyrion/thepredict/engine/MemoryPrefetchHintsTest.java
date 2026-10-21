package akyrion.thepredict.engine;

import akyrion.thepredict.domain.HintKind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryPrefetchHintsTest {
    @Test
    void aRefusedPreLoginOffersNothingAndAFullQueueDrops() {
        var hints = new MemoryPrefetchHints();
        var subject = new UUID(1, 2);
        assertFalse(hints.offer(subject, HintKind.PRE_LOGIN, false));
        assertEquals(0, hints.drain(16).size());
        assertTrue(hints.offer(subject, HintKind.PRE_LOGIN, true));
        assertTrue(hints.offer(subject, HintKind.JOIN, true));
        assertEquals(2, hints.drain(16).size());
        for (var index = 0; index < MemoryPrefetchHints.CAPACITY; index++) {
            assertTrue(hints.offer(new UUID(0, index), HintKind.QUIT, true));
        }
        assertFalse(hints.offer(subject, HintKind.JOIN, true));
        assertEquals(1, hints.dropped());
    }
}
