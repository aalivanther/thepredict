package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnedValueConsiderationTest {
    @Test
    void weakIsNeutralAndANarrowSpreadLeavesEveryUsableFactorAtOne() {
        var flat = LearnedValueConsideration.factors(new int[] {10, 20}, new boolean[] {true, true}, 20);
        assertEquals(1.0, flat[0]);
        assertEquals(1.0, flat[1]);
        var spread = LearnedValueConsideration.factors(new int[] {0, 100, 50}, new boolean[] {true, true, false}, 20);
        assertEquals(0.2, spread[0], 1.0e-12);
        assertEquals(1.0, spread[1], 1.0e-12);
        assertEquals(LearnedValueConsideration.NEUTRAL, spread[2]);
        assertEquals(500, LearnedValueConsideration.scoreMillis(0.5, 1.0));
    }

    @Test
    void oneUsableValueDoesNotLiftItsCandidateOverABetterBase() {
        var factors = LearnedValueConsideration.factors(new int[] {-800, 0}, new boolean[] {true, false}, 20);
        assertArrayEquals(new double[] {1.0, 1.0}, factors, 0.0);
        var usable = LearnedValueConsideration.scoreMillis(0.5, factors[0]);
        var weak = LearnedValueConsideration.scoreMillis(0.7, factors[1]);
        assertTrue(weak > usable, "the base order stands without a value signal");
        var narrow = LearnedValueConsideration.factors(new int[] {300, 310, 0}, new boolean[] {true, true, false}, 20);
        assertArrayEquals(new double[] {1.0, 1.0, 1.0}, narrow, 0.0);
        var equal = LearnedValueConsideration.factors(new int[] {300, 300}, new boolean[] {true, true}, 0);
        assertArrayEquals(new double[] {1.0, 1.0}, equal, 0.0);
    }
}
