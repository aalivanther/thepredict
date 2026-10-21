package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step limit and damping (M22.8). */
class DifficultyPolicyTest {
    @Test
    void theSlowStepNeverExceedsOneAndFourReversalsDamp() {
        var gain = DifficultyGlobals.DEFAULT.effectiveGain();
        for (var observed = 1.0; observed <= 3600.0; observed *= 1.5) {
            var delta = DifficultyPolicy.deltaU(Math.log(observed), Math.log(1800.0), 0.10, gain,
                    DifficultyGlobals.LADDER_K, true);
            assertTrue(Math.abs(delta) <= 1.0, "delta " + delta);
        }
        var open = DifficultyPolicy.deltaU(Math.log(1.0), Math.log(1800.0), 0.0, 2.2, 0.12, false);
        assertTrue(Math.abs(open) > 1.0);
        assertEquals(0, DifficultyPolicy.dampings(3, 4));
        assertEquals(1, DifficultyPolicy.dampings(4, 4));
        assertEquals(0.125, DifficultyPolicy.dampedGain(1.0, 3), 1e-12);
    }

    @Test
    void theNextPriorClampsTheStepAndIgnoresOneThatIsNotFinite() {
        assertEquals(4.5, DifficultyPolicy.nextU(4.0, 0.5, 0, 8), 1e-12);
        assertEquals(8.0, DifficultyPolicy.nextU(7.5, 1.0, 0, 8), 1e-12);
        assertEquals(0.0, DifficultyPolicy.nextU(0.5, -1.0, 0, 8), 1e-12);
        for (var step : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(4.0, DifficultyPolicy.nextU(4.0, step, 0, 8), 1e-12, "step " + step);
        }
        var poisoned = DifficultyPolicy.deltaU(Double.NaN, Math.log(1800.0), 0.10, 0.25, DifficultyGlobals.LADDER_K,
                true);
        assertEquals(4.0, DifficultyPolicy.nextU(4.0, poisoned, 0, 8), 1e-12);
    }

    @Test
    void twoOutsideReadsMoveOneRungAndAMissedHitDoesNot() {
        var state = new DifficultyPolicy.Fast(4, 0, 0);
        var first = DifficultyPolicy.fast(state, 90.0, 60.0, 0.20, true, false, 0, 8);
        assertEquals(4, first.rung());
        assertEquals(1, first.outsideStreak());
        var second = DifficultyPolicy.fast(first, 90.0, 60.0, 0.20, true, false, 0, 8);
        assertEquals(5, second.rung());
        var idle = DifficultyPolicy.fast(first, 90.0, 60.0, 0.20, false, false, 0, 8);
        assertEquals(first, idle);
        var busy = DifficultyPolicy.fast(first, 90.0, 60.0, 0.20, true, true, 0, 8);
        assertEquals(4, busy.rung());
        assertEquals(2, busy.outsideStreak());
    }
}
