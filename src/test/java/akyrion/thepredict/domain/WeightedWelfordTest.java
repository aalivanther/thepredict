package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightedWelfordTest {
    @Test
    void equalWeightsAverageTheMeans() {
        var mixed = PlayerMemoryMath.welford(1, 2, 0, 1, 4, 0);
        assertEquals(2, mixed[0], 0.0);
        assertEquals(3, mixed[1], 1e-9);
        var skipped = PlayerMemoryMath.welford(1, 2, 0, 0, 9, 0);
        assertEquals(2, skipped[1], 0.0);
    }
}
