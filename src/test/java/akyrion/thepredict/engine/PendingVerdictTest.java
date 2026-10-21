package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pending keeps the previous policy and does not wait (M22.5, B5). */
class PendingVerdictTest {
    @Test
    void nothingLoadedStaysPendingAndReturnsImmediately() {
        var gates = new GateVerdicts();
        var fastest = Long.MAX_VALUE;
        for (var attempt = 0; attempt < 5; attempt++) {
            var started = System.nanoTime();
            assertEquals(GateVerdicts.Status.PENDING, gates.status("candidate"));
            fastest = Math.min(fastest, System.nanoTime() - started);
        }
        assertEquals("", gates.takeArmed());
        assertTrue(fastest < 50_000_000L, "a pending verdict must not wait; the fastest of five calls took " + fastest);
        assertTrue(gates.due(1L));
    }
}
