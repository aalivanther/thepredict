package akyrion.thepredict.engine;

import akyrion.thepredict.domain.GroupFairnessReason;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Impact gap against every other member, before and after (M25, B2). */
class ImpactGapTest {
    @Test
    void theGapRejectsBothSidesAndAllowsEquality() {
        var ledger = new AttackLedger();
        assertTrue(ledger.reserve(0, 0, 30, 0, 40, 6).reason().isEmpty());
        assertEquals(GroupFairnessReason.IMPACT_GAP, ledger.reserve(0, 1, 27, 0, 40, 6).reason().orElseThrow());
        assertTrue(ledger.reserve(0, 1, 20, 0, 40, 6).reason().isEmpty());
        assertEquals(GroupFairnessReason.IMPACT_GAP, ledger.reserve(0, 2, 24, 0, 40, 6).reason().orElseThrow());
        assertTrue(ledger.reserve(0, 2, 36, 0, 40, 6).reason().isEmpty());
    }

    @Test
    void aPastReservationStillBlocksTheGap() {
        var ledger = new AttackLedger();
        assertTrue(ledger.reserve(0, 0, 30, 0, 40, 6).reason().isEmpty());
        assertEquals(GroupFairnessReason.IMPACT_GAP, ledger.reserve(0, 1, 33, 100, 40, 6).reason().orElseThrow());
        assertTrue(ledger.reserve(0, 1, 24, 100, 40, 6).reason().isEmpty());
    }
}
