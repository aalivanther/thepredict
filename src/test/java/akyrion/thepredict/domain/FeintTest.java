package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Feints use {@link StableRandom}. Out of range is a load error, never a clip (errata E23-9). */
class FeintTest {
    @Test
    void commitmentOneHundredNeverFeintsAndFiftyIsHalf() {
        var full = 0;
        var half = 0;
        for (var sequence = 0; sequence < 10_000; sequence++) {
            if (TelegraphPolicy.feint(23L, sequence, 100)) {
                full++;
            }
            if (TelegraphPolicy.feint(23L, sequence, 50)) {
                half++;
            }
            assertEquals(TelegraphPolicy.feint(23L, sequence, 50), TelegraphPolicy.feint(23L, sequence, 50));
        }
        assertEquals(0, full);
        assertEquals(0.50, half / 10_000.0, 0.02);
    }

    @Test
    void aZeroWindUpDoesNotOpenAndOutOfRangeCommitmentIsAnError() {
        assertFalse(TelegraphPolicy.opens(true, false, DangerClass.MEDIUM, 0));
        assertFalse(TelegraphPolicy.opens(true, true, DangerClass.LIGHT, 0));
        assertTrue(TelegraphPolicy.opens(true, false, DangerClass.MEDIUM, 4));
        assertThrows(IllegalArgumentException.class, () -> TelegraphSettings.DEFAULT.withCommitment(30, 25));
        assertThrows(IllegalArgumentException.class, () -> TelegraphSettings.DEFAULT.withCommitment(100, 80));
        assertEquals(25, TelegraphSettings.DEFAULT.entropyCommitmentPercent());
    }
}
