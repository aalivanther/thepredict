package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilityScoreTest {
    @Test
    void aZeroFactorVetoesAndTheProductIsScaled() {
        assertEquals(0, UtilityScore.scoreMillis(new double[] {1, 0, 1}, false));
        assertEquals(1000, UtilityScore.scoreMillis(new double[] {1, 1}, false));
        assertEquals(0.1, UtilityScore.priority(0));
    }
}
