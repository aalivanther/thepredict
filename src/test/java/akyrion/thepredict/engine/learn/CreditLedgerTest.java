package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.learn.CreditMath;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditLedgerTest {
    private static final String FINE = "v2:MELEE:HIGH:LOW:MOB:OPEN:SOLO:NONE";
    private static final long OCTOBER = 1_791_900_000_000L;

    @Test
    void thirtyThreeWindowsDropTheOldestAndMarkTruncation() {
        var ledger = new CreditLedger();
        for (var index = 0; index < 33; index++) {
            ledger.onWindowClosed("t" + index, FINE, 1, 0.0, true, true);
        }
        assertTrue(ledger.truncated());
        var dropped = ledger.truncatedRows("guardian", 1, 10L);
        assertEquals(1, dropped.size());
        assertEquals("t0", dropped.getFirst().key().tactic());
        assertEquals(0.0, dropped.getFirst().creditSum(), 0.0);
        assertEquals(LearnCause.CREDIT_TRUNCATED.name(), dropped.getFirst().key().source());
        var rows = ledger.onEncounterClosed("guardian", "sim", 1, 1.0, 10L);
        assertEquals(32, rows.size());
        assertEquals("t1", rows.getFirst().key().tactic());
        assertEquals(CreditMath.deferred(1.0, 31), rows.getFirst().creditSum(), 1.0e-12);
        assertEquals(1.0, rows.getLast().creditSum(), 1.0e-12);
        assertFalse(ledger.truncated());
        var shutdown = new CreditLedger();
        shutdown.onWindowClosed("press", "v2", 1, 0.0, false, false);
        var closed = shutdown.onShutdown("guardian", 1, 10L);
        assertEquals("SHUTDOWN", closed.getFirst().key().source());
        assertEquals(0.0, closed.getFirst().creditSum(), 0.0);
    }

    @Test
    void aDroppedWindowLosesOnlyItsCreditNotItsEffectOrAvoidedDamage() {
        var ledger = new CreditLedger();
        ledger.onWindowClosed("first", FINE, 1.0, 0.3, false, false, TrainingSource.BENCH);
        for (var index = 0; index < CreditLedger.CAP; index++) {
            ledger.onWindowClosed("t" + index, FINE, 0.0, 0.0, false, false, TrainingSource.BENCH);
        }
        var rows = ledger.onEncounterClosed("guardian", 1.0, OCTOBER);
        var dropped = rows.getFirst();
        assertEquals("first", dropped.key().tactic());
        assertEquals("bench", dropped.key().source());
        assertEquals(0.0, dropped.creditSum(), 0.0);
        assertEquals(CreditMath.effectTerm(1.0), dropped.effectSum(), 1.0e-12);
        assertEquals(0.3, dropped.avoidedSum(), 1.0e-12);
        assertEquals(CreditMath.reward(CreditMath.effectTerm(1.0), 0.0, 0.3), dropped.rewardMean(), 1.0e-12);
        assertEquals(CreditLedger.CAP + 1, rows.size());
    }

    @Test
    void everyRowKeepsTheSourceOfItsWindowAndTheWeekOfTheWallTime() {
        var ledger = new CreditLedger();
        ledger.onWindowClosed("press", FINE, 1.0, 0.0, true, true, TrainingSource.PLAYER_PACED);
        ledger.onWindowClosed("guard", FINE, 0.0, 0.0, true, false, TrainingSource.PLAYER_FULL);
        var rows = ledger.onEncounterClosed("guardian", 1.0, OCTOBER);
        assertEquals("player_paced", rows.get(0).key().source());
        assertEquals("player_full", rows.get(1).key().source());
        for (var row : rows) {
            assertEquals(ValueStatKey.week(OCTOBER), row.key().week());
            assertTrue(row.key().week() > 0);
        }
        assertEquals(1, rows.get(0).jevSeen());
        assertEquals(1, rows.get(0).jevAgree());
        assertEquals(0, rows.get(1).jevAgree());
    }

    @Test
    void legacyRowsKeepTheCallerWeekSoAnUnknownSourceNeverTrains() {
        var ledger = new CreditLedger();
        ledger.onWindowClosed("press", FINE, 1.0, 0.0, false, false);
        var rows = new java.util.ArrayList<>(ledger.onEncounterClosed("guardian", "sim", 0, 1.0, OCTOBER));
        ledger.onWindowClosed("press", FINE, 1.0, 0.0, false, false);
        for (var index = 0; index < CreditLedger.CAP; index++) {
            ledger.onWindowClosed("t" + index, FINE, 0.0, 0.0, false, false);
        }
        rows.addAll(ledger.truncatedRows("guardian", 0, OCTOBER));
        rows.addAll(ledger.onShutdown("guardian", 0, OCTOBER));
        for (var row : rows) {
            assertEquals(0, row.key().week());
        }
        var build = akyrion.thepredict.domain.learn.ValueRollup.of(LearningSettings.DEFAULT);
        build.add(rows);
        assertEquals(rows.size(), build.skipped());
        var sourced = new CreditLedger();
        sourced.onWindowClosed("press", FINE, 1.0, 0.0, false, false, TrainingSource.SIM);
        var kept = akyrion.thepredict.domain.learn.ValueRollup.of(LearningSettings.DEFAULT);
        kept.add(sourced.onEncounterClosed("guardian", 1.0, OCTOBER));
        assertEquals(0, kept.skipped());
    }

    @Test
    void shutdownKeepsTheWindowSourceWithZeroTerminal() {
        var ledger = new CreditLedger();
        ledger.onWindowClosed("press", FINE, 1.0, 0.0, false, false, TrainingSource.PLAYER_PACED);
        var rows = ledger.onShutdown("guardian", OCTOBER);
        assertEquals(1, rows.size());
        assertEquals("player_paced", rows.getFirst().key().source());
        assertEquals(0.0, rows.getFirst().creditSum(), 0.0);
        assertTrue(ledger.onShutdown("guardian", OCTOBER).isEmpty());
    }

    @Test
    void agreementWithoutAQuestionIsNotCounted() {
        var ledger = new CreditLedger();
        ledger.onWindowClosed("press", FINE, 1.0, 0.0, false, true, TrainingSource.BENCH);
        var row = ledger.onEncounterClosed("guardian", 0.0, OCTOBER).getFirst();
        assertEquals(0, row.jevSeen());
        assertEquals(0, row.jevAgree());
    }

    @Test
    void aLongFightFoldsDroppedWindowsWithTheirExactMeanAndSpread() {
        var ledger = new CreditLedger();
        var share = LearningSettings.DEFAULT.creditShare();
        var effects = new double[] {1.0, -0.6, 2.4, 0.2, -1.0};
        var avoided = new double[] {0.0, 0.2, 0.0, 0.5, 0.1};
        for (var index = 0; index < effects.length; index++) {
            ledger.onWindowClosed("press", FINE, effects[index], avoided[index], false, false, TrainingSource.BENCH);
        }
        for (var index = 0; index < CreditLedger.CAP; index++) {
            ledger.onWindowClosed("hold", FINE, 0.0, 0.0, false, false, TrainingSource.BENCH);
        }
        var folded = ledger.onEncounterClosed("guardian", 1.0, OCTOBER).getFirst();
        var rewards = new double[effects.length];
        for (var index = 0; index < effects.length; index++) {
            rewards[index] = CreditMath.reward(CreditMath.effectTerm(effects[index]), 0.0, avoided[index], share);
        }
        assertEquals("press", folded.key().tactic());
        assertEquals(effects.length, folded.windows());
        assertEquals(mean(rewards), folded.rewardMean(), 1.0e-12);
        assertEquals(m2(rewards), folded.rewardM2(), 1.0e-12);
        assertEquals(0.0, folded.creditSum(), 0.0);
    }

    @Test
    void overflowKeysAreCappedAndTheRestIsCountedAsLost() {
        var overflow = new CreditOverflow();
        for (var index = 0; index < CreditOverflow.KEYS + 7; index++) {
            overflow.add("t" + index, FINE, TrainingSource.BENCH, 0.5, 0.0, false, false);
        }
        assertEquals(CreditOverflow.KEYS, overflow.keys());
        assertEquals(7, overflow.lost());
        List<ValueStatDelta> rows = overflow.drain("guardian", "unknown", 1, 0.5, OCTOBER);
        assertEquals(CreditOverflow.KEYS, rows.size());
        assertEquals(0, overflow.keys());
        assertEquals(0, overflow.lost());
    }

    private static double mean(double[] values) {
        var sum = 0.0;
        for (var value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double m2(double[] values) {
        var center = mean(values);
        var sum = 0.0;
        for (var value : values) {
            sum += (value - center) * (value - center);
        }
        return sum;
    }
}
