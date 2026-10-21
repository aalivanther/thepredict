package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.HintKind;
import akyrion.thepredict.engine.MemoryPrefetchHints;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MemoryPresenceListenerTest {
    @Test
    void preLoginOffersOnlyWhenAllowed() {
        var hints = new MemoryPrefetchHints();
        var listener = new MemoryPresenceListener(hints);
        var subject = new UUID(5, 6);
        assertFalse(listener.onPreLogin(subject, false));
        listener.onPreLogin(subject, true);
        listener.onJoin(subject);
        listener.onQuit(subject);
        var drained = hints.drain(16);
        assertEquals(3, drained.size());
        assertEquals(HintKind.PRE_LOGIN, drained.get(0).kind());
        assertEquals(HintKind.JOIN, drained.get(1).kind());
        assertEquals(HintKind.QUIT, drained.get(2).kind());
    }
}
