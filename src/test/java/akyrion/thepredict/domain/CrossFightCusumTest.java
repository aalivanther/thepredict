package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossFightCusumTest {
    @Test
    void aBreakScalesMemoryBeforeGammaAndNeverPassesFour() {
        var updated = PlayerMemoryMath.update(20, 2, 0, 0, 8, 0.9, 4, 0.25, 0.6);
        assertTrue(updated.breaks() >= 1);
        assertTrue(updated.breaks() <= PlayerMemoryMath.MAX_BREAKS);
        assertEquals(0.9 * 20 * Math.pow(0.25, updated.breaks()) + 0, updated.successes(), 1e-9);
        var cold = PlayerMemoryMath.update(0.2, 0.2, 5, 1, 0, 0.9, 4, 0.25, 0.6);
        assertEquals(0, cold.breaks());
        assertEquals(0.0, cold.cusum(), 0.0);
    }
}
