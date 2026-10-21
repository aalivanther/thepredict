package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatWeightsTest {
    @Test
    void zeroWeightsAreAccepted() {
        assertEquals(0, new ThreatWeights(0, 0, 0, 0, 0).maxPerWindow());
    }

    @Test
    void negativeOrNonFiniteWeightsAreRejectedByName() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ThreatWeights(-1, 0, 0, 0, 0))
                .getMessage().contains("assistsOthers"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ThreatWeights(0, Double.NaN, 0, 0, 0))
                .getMessage().contains("rangedHarass"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ThreatWeights(0, 0, 0, 0,
                Double.POSITIVE_INFINITY)).getMessage().contains("maxPerWindow"));
    }
}
