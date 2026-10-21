package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.TokenEnd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A locked token survives idle time, a cancel, and a membership epoch (M25, rule 7 bis). */
class LockedTokenEpochTest {
    @Test
    void aLockedTokenSurvivesIdleCancelAndANewEpoch() {
        var ledger = new AttackLedger();
        assertTrue(ledger.grant(0, 1, AttackPool.MELEE, 10, 2, 1).isEmpty());
        ledger.lock(0, 1, AttackPool.MELEE);
        assertTrue(ledger.holds(0, 1, AttackPool.MELEE, 10_000, 30));
        ledger.cancel(0, 1);
        assertTrue(ledger.holds(0, 1, AttackPool.MELEE, 10_000, 30));
        var copy = new GroupCopy();
        copy.publish(1, 1, GroupRole.PRESSURE, -1, 1, 0, 1, 1, GroupMode.FAIR);
        copy.noteEpoch(2, 1);
        assertEquals(1, copy.tokenMask());
        assertTrue(copy.stale());
        assertEquals(GroupMode.FAIR, copy.mode());
    }

    @Test
    void yieldedIsLastAndThereIsNoEpochEnd() {
        var values = TokenEnd.values();
        assertEquals(TokenEnd.YIELDED, values[values.length - 1]);
        for (var end : values) {
            assertFalse(end.name().equals("EPOCH"));
        }
    }

    @Test
    void theSeventeenthReservationWaits() {
        var ledger = new AttackLedger();
        for (var index = 0; index < 16; index++) {
            var reserved = ledger.reserve(0, index, 100 + index, 1, 40, 0);
            assertTrue(reserved.reason().isEmpty(), reserved.toString());
        }
        var full = ledger.reserve(0, 0, 200, 1, 40, 0);
        assertEquals(GroupFairnessReason.RESERVATION_RING_FULL, full.reason().orElseThrow());
        assertEquals(16, ledger.size(0));
    }

    @Test
    void aFreshCopyKeepsCoordinate() {
        var copy = new GroupCopy();
        copy.publish(3, 2, GroupRole.GUARD, -1, 4, 1, 0, 1, GroupMode.COORDINATE);
        copy.noteEpoch(3, 2);
        assertFalse(copy.stale());
        assertEquals(GroupMode.COORDINATE, copy.mode());
    }
}
