package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The transition sketch of M27.2: equal keys merge, a ninth key subtracts its weight from all, zero drops out. */
class MisraGriesReduceTest {
    @Test
    void nineKeysCollapseToEightBySubtractingTheNinthWeight() {
        var keys = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
        var weights = new float[] {9, 8, 7, 6, 5, 4, 3, 2, 1};
        var sketch = TransitionReducer.merge(keys, weights, 1f, new int[0], new float[0]);
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6, 7, 8}, sketch.keys());
        assertArrayEquals(new float[] {8, 7, 6, 5, 4, 3, 2, 1}, sketch.weights());
        assertEquals(TransitionReducer.KEPT, sketch.keys().length);
    }

    @Test
    void storedWeightsAreScaledAndEqualKeysMerge() {
        var sketch = TransitionReducer.merge(new int[] {1, 2}, new float[] {4f, 2f}, 0.5f, new int[] {2, 3},
                new float[] {3f, 1f});
        assertArrayEquals(new int[] {1, 2, 3}, sketch.keys());
        assertArrayEquals(new float[] {2f, 4f, 1f}, sketch.weights());
        var forgotten = TransitionReducer.merge(new int[] {1, 2}, new float[] {4f, 2f}, 0f, new int[] {3},
                new float[] {1f});
        assertArrayEquals(new int[] {3}, forgotten.keys());
    }
}
