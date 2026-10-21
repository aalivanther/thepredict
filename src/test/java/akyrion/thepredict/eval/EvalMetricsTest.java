package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CIF median, entropy and an unwarned hit on the floor (M22.1). */
class EvalMetricsTest {
    @Test
    void theMedianIsADashBelowOneHalfAndEntropyUsesTheTacticCount() {
        assertEquals("30", EvalMetrics.median(new double[] {0.2, 0.6, 0.7}, new int[] {10, 30, 90}));
        assertEquals("—", EvalMetrics.median(new double[] {0.2, 0.4}, new int[] {10, 90}));
        assertEquals(0.0, EvalMetrics.entropy(new int[] {6}), 1e-9);
        assertEquals(1.0, EvalMetrics.entropy(new int[] {1, 1, 1, 1, 1, 1}), 1e-9);
        assertTrue(EvalMetrics.unwarnedAtFloor(5, 5));
        assertTrue(EvalMetrics.unwarnedAtFloor(5, 4));
        assertTrue(EvalMetrics.unwarnedAtFloor(5, 6));
        assertFalse(EvalMetrics.unwarnedAtFloor(5, 8));
    }
}
