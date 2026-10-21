package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.TellAnswer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spacing of big tells in both directions, and the leader reserve (M25, B2, N-A, N-I). */
class TellLedgerTest {
    @Test
    void aSevereBlocksAMediumOfTheSameAnswerOnEitherSide() {
        var ledger = new TellLedger();
        ledger.open(0, DangerClass.SEVERE, TellAnswer.BLOCK, 30, false);
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING, medium(ledger, 38));
        assertTrue(ledger.admit(0, DangerClass.MEDIUM, TellAnswer.BLOCK, 40, 0, 10, 2, true, false).isEmpty());
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING, medium(ledger, 21));
    }

    @Test
    void aPastSevereStillSpacesAMedium() {
        var ledger = new TellLedger();
        ledger.open(0, DangerClass.SEVERE, TellAnswer.BLOCK, 30, false);
        ledger.close(0, DangerClass.SEVERE, false, 30);
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING,
                ledger.admit(0, DangerClass.MEDIUM, TellAnswer.BLOCK, 38, 100, 10, 2, true, false).orElseThrow());
        assertTrue(ledger.admit(0, DangerClass.MEDIUM, TellAnswer.BLOCK, 40, 100, 10, 2, true, false).isEmpty());
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING,
                ledger.admit(0, DangerClass.MEDIUM, TellAnswer.BLOCK, 21, 100, 10, 2, true, false).orElseThrow());
    }

    @Test
    void aMediumBlocksALaterSevereOnEitherSide() {
        var ledger = new TellLedger();
        ledger.open(0, DangerClass.MEDIUM, TellAnswer.BLOCK, 20, false);
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING, severe(ledger, 28));
        assertTrue(ledger.admit(0, DangerClass.SEVERE, TellAnswer.BLOCK, 30, 0, 10, 2, true, false).isEmpty());
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING, severe(ledger, 11));
    }

    @Test
    void twoMediumsDoNotUseTheBigSpacing() {
        var ledger = new TellLedger();
        ledger.open(0, DangerClass.MEDIUM, TellAnswer.BLOCK, 20, false);
        assertTrue(ledger.admit(0, DangerClass.MEDIUM, TellAnswer.BLOCK, 28, 0, 10, 2, true, false).isEmpty());
    }

    @Test
    void anUnopenedReserveExpiresAndStaysQuietForFortyTicks() {
        var ledger = new TellLedger();
        assertTrue(ledger.reserveLeader(0, 0, 10, 30));
        assertTrue(ledger.leaderHolding(0, 19));
        assertFalse(ledger.expireLeader(0, 19));
        assertTrue(ledger.expireLeader(0, 20));
        assertFalse(ledger.leaderHolding(0, 20));
        assertFalse(ledger.reserveLeader(0, 21, 10, 40));
        assertFalse(ledger.reserveLeader(0, 59, 10, 40));
        assertTrue(ledger.reserveLeader(0, 60, 10, 70));
        assertTrue(ledger.leaderHolding(0, 60));
    }

    @Test
    void theLeaderOpeningClearsTheReserve() {
        var ledger = new TellLedger();
        assertTrue(ledger.reserveLeader(0, 0, 10, 30));
        ledger.open(0, DangerClass.HEAVY, TellAnswer.BLOCK, 30, true);
        assertFalse(ledger.leaderHolding(0, 1));
        assertTrue(ledger.reserveLeader(0, 1, 10, 40));
    }

    @Test
    void everyEntryTreatsAPlayerIndexOutsideTheSixteenRowsAlike() {
        var ledger = new TellLedger();
        for (var player : new int[] {-1, 16}) {
            assertTrue(ledger.admit(player, DangerClass.SEVERE, TellAnswer.BLOCK, 30, 0, 10, 2, true, false)
                    .isEmpty(), "no rows to hold back with, player " + player);
            ledger.open(player, DangerClass.SEVERE, TellAnswer.BLOCK, 30, false);
            ledger.close(player, DangerClass.SEVERE, false, 30);
            assertFalse(ledger.reserveLeader(player, 0, 10, 30));
            assertFalse(ledger.expireLeader(player, 100));
            assertFalse(ledger.leaderHolding(player, 0));
        }
        assertTrue(ledger.admit(0, DangerClass.SEVERE, TellAnswer.BLOCK, 30, 0, 10, 2, true, false).isEmpty(),
                "a row that was never used stays free");
    }

    private static GroupFairnessReason medium(TellLedger ledger, long impact) {
        return ledger.admit(0, DangerClass.MEDIUM, TellAnswer.BLOCK, impact, 0, 10, 2, true, false).orElseThrow();
    }

    private static GroupFairnessReason severe(TellLedger ledger, long impact) {
        return ledger.admit(0, DangerClass.SEVERE, TellAnswer.BLOCK, impact, 0, 10, 2, true, false).orElseThrow();
    }
}
