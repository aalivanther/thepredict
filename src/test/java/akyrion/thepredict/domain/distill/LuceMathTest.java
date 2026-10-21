package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fifty MM iterations, fixed tie break. The same sets produce the same strengths. */
class LuceMathTest {
    @Test
    void fiftyIterationsPreferTheTacticThatAlwaysWins() {
        var sets = new int[][] {{0, 1}, {0, 1}};
        var chosen = new int[] {0, 0};
        var once = LuceMath.fit(sets, chosen, 2);
        var again = LuceMath.fit(sets, chosen, 2);
        assertEquals(LuceMath.ITERATIONS, 50);
        assertEquals(once[0], again[0]);
        assertTrue(once[0] > once[1]);
        assertEquals(1.0, once[0] + once[1], 1e-9);
    }

    @Test
    void tenThousandSyntheticChoicesRecoverKnownStrengths() {
        var truth = new double[] {0.5, 0.3, 0.2};
        var random = new java.util.SplittableRandom(29L);
        var sets = new int[10_000][];
        var chosen = new int[sets.length];
        for (var round = 0; round < sets.length; round++) {
            sets[round] = random.nextBoolean() ? new int[] {0, 1, 2} : pair(random.nextInt(3));
            chosen[round] = draw(sets[round], truth, random.nextDouble());
        }
        var fitted = LuceMath.fit(sets, chosen, 4);
        for (var tactic = 0; tactic < truth.length; tactic++) {
            assertEquals(truth[tactic], fitted[tactic] / (1.0 - fitted[3]), 0.05, "tactic " + tactic);
        }
        assertTrue(fitted[3] > 0.0, "a tactic never offered stays at the prior");
        var step = LuceMath.step(sets, chosen, new double[] {1.0, 1.0, 1.0, 1.0});
        assertEquals(LuceMath.PRIOR, step[3], 0.0);
    }

    private static int[] pair(int left) {
        return new int[] {left, (left + 1) % 3};
    }

    private static int draw(int[] set, double[] truth, double unit) {
        var sum = 0.0;
        for (var tactic : set) {
            sum += truth[tactic];
        }
        var cursor = unit * sum;
        for (var tactic : set) {
            cursor -= truth[tactic];
            if (cursor < 0.0) {
                return tactic;
            }
        }
        return set[set.length - 1];
    }
}
