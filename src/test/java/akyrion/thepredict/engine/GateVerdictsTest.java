package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A later epoch wins and a pass arms on the next tick (M22.5). */
class GateVerdictsTest {
    @Test
    void theLaterEpochWinsAndArmingWaitsForTheNextTake() {
        var gates = new GateVerdicts();
        assertEquals(GateVerdicts.Status.PENDING, gates.status("abc"));
        assertTrue(gates.due(0L));
        assertFalse(gates.due(10L));
        assertTrue(gates.due(GateVerdicts.REQUEST_EVERY_TICKS));
        gates.load(List.of(new StorageReply.GateVerdictRow("abc", "G1", "FAIL", "run", 10L),
                new StorageReply.GateVerdictRow("abc", "G1", "PASS", "run", 20L),
                new StorageReply.GateVerdictRow("abc", "G1", "FAIL", "run", 20L)));
        assertEquals(GateVerdicts.Status.PASS, gates.status("abc"));
        assertEquals(GateVerdicts.Status.NONE, gates.status("missing"));
        gates.armNext("abc");
        assertEquals("abc", gates.takeArmed());
        assertEquals("", gates.takeArmed());
        assertFalse(gates.due(10_000L));
    }
}
