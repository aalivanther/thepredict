package akyrion.thepredict.adapter.server.patterns;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeRingTest {
    /** SP-10: after eight attackers, two new ones on the same tick keep their own charge and flags. */
    @Test
    void aStaleSlotIsFreedSoTwoNewAttackersDoNotShareSlotZero() {
        var ring = new ChargeRing();
        for (var index = 0; index < 8; index++) {
            ring.note(new UUID(1, index), 1, 0.5, false, false);
        }
        var first = new UUID(2, 1);
        var second = new UUID(2, 2);
        ring.note(first, 10, 0.3, true, false);
        ring.note(second, 10, 0.4, false, true);
        ring.flag(first, 10, false, true, false);
        assertEquals(0.3, ring.take(first, 10).charge());
        assertTrue(ring.take(first, 10).sprinting());
        assertEquals(0.4, ring.take(second, 10).charge());
        assertTrue(ring.take(second, 10).spear());
        assertTrue(ring.flags(first, 10).right());
        assertFalse(ring.flags(second, 10).right());
    }

    /** A slot noted or flagged on this tick or the previous one is live; a ring full of live slots evicts one. */
    @Test
    void aLiveSlotIsKeptAndAFullRingOfLiveSlotsEvictsExactlyOne() {
        var ring = new ChargeRing();
        for (var index = 0; index < 8; index++) {
            ring.note(new UUID(1, index), 10, 0.1 * (index + 1), false, false);
        }
        ring.note(new UUID(3, 3), 11, 0.9, false, false);
        assertEquals(0.9, ring.take(new UUID(3, 3), 11).charge());
        var kept = 0;
        for (var index = 0; index < 8; index++) {
            if (ring.take(new UUID(1, index), 10).charge() == 0.1 * (index + 1)) {
                kept++;
            }
        }
        assertEquals(7, kept);
    }
}
