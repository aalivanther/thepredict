package akyrion.thepredict.adapter.jev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** M19.9: the bank pruning report has no clock, so two builds are the same string. */
class BankPruningReportTest {
    @Test
    void reportIsByteForByteStable() {
        var first = BankPruningReport.text();
        assertEquals(first, BankPruningReport.text());
        assertFalse(first.isBlank());
    }
}
