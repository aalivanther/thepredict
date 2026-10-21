package akyrion.thepredict.domain;

import java.util.Arrays;

/**
 * Seed masses for the 24 Beta patterns (M27.5). Fixed cadence is not seeded. {@link #NONE} is all zeros.
 *
 * @param successes success mass per Beta ordinal
 * @param failures failure mass per Beta ordinal
 */
public record PatternSeed(double[] successes, double[] failures) {
    /** No seed. */
    public static final PatternSeed NONE = new PatternSeed(new double[EnemyProfileSummary.BETA],
            new double[EnemyProfileSummary.BETA]);

    /**
     * Copies the arrays.
     *
     * @throws IllegalArgumentException if either array is not 24 long
     */
    public PatternSeed {
        if (successes.length != EnemyProfileSummary.BETA || failures.length != EnemyProfileSummary.BETA) {
            throw new IllegalArgumentException("seed");
        }
        successes = Arrays.copyOf(successes, successes.length);
        failures = Arrays.copyOf(failures, failures.length);
    }

    /**
     * Total pseudo-trials. Not rounded: the masses carry the forgetting factor, so the sum is fractional.
     *
     * @return the sum of both masses
     */
    public double trials() {
        var total = 0.0;
        for (var index = 0; index < successes.length; index++) {
            total += successes[index] + failures[index];
        }
        return total;
    }
}
