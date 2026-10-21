package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Folding a fight into a stored style (M27.2): transitions merge by key, totals stay exact, variance stays put. */
class StyleFoldTest {
    private static final double GAMMA = 0.5;

    @Test
    void aKeyStoredAndSeenInTheFightBecomesOneEntryWithTheSummedWeight() {
        var stored = style(new int[] {5, 7}, new float[] {10f, 4f}, 20f, 0f, 0f, 0f);
        var fight = style(new int[] {5}, new float[] {3f}, 3f, 0f, 0f, 0f);
        var folded = fold(stored, fight);
        assertArrayEquals(new int[] {5, 7}, folded.transitionKeys());
        assertArrayEquals(new float[] {(float) (GAMMA * 10 + 3), (float) (GAMMA * 4)}, folded.transitionWeights());
        assertEquals((float) (GAMMA * 20 + 3), folded.transitionTotal());
    }

    @Test
    void moreThanEightKeysAreReducedBySubtractingTheNinth() {
        var keys = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
        var weights = new float[] {18f, 16f, 14f, 12f, 10f, 8f, 6f, 4f, 2f};
        var folded = fold(style(keys, weights, 90f, 0f, 0f, 0f), style(new int[0], new float[0], 0f, 0f, 0f, 0f));
        assertEquals(TransitionReducer.KEPT, folded.transitionKeys().length);
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6, 7, 8}, folded.transitionKeys());
        assertEquals((float) (GAMMA * 18 - GAMMA * 2), folded.transitionWeights()[0]);
        assertEquals((float) (GAMMA * 90), folded.transitionTotal());
    }

    @Test
    void forgettingScalesTheReactionMomentWithItsWeight() {
        var stored = style(new int[0], new float[0], 0f, 4f, 10f, 8f);
        var folded = fold(stored, style(new int[0], new float[0], 0f, 0f, 0f, 0f));
        assertEquals((float) (GAMMA * 4), folded.reactionWeight());
        assertEquals(10f, folded.reactionMean());
        assertEquals((float) (GAMMA * 8), folded.reactionM2());
        assertEquals(8f / 4f, folded.reactionM2() / folded.reactionWeight());
    }

    private static StyleMemory fold(StyleMemory stored, StyleMemory fight) {
        return StyleFold.apply(Optional.of(stored), Optional.of(fight), GAMMA, 4.0, 0.25).style().orElseThrow();
    }

    private static StyleMemory style(int[] keys, float[] weights, float total, float reactionWeight,
                                     float reactionMean, float reactionM2) {
        return new StyleMemory(1f, 0, new float[StyleMemory.PATTERNS], new float[StyleMemory.PATTERNS],
                new int[StyleMemory.PATTERNS], new float[StyleMemory.ACTIONS], total, keys, weights,
                new float[StyleMemory.HISTOGRAMS], new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS], reactionWeight,
                reactionMean, reactionM2, new float[StyleMemory.EPISODES], new float[StyleMemory.EPISODES]);
    }
}
