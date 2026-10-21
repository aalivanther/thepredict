package akyrion.thepredict.domain.learn;

/**
 * Factor of {@link akyrion.thepredict.domain.Consideration#LEARNED_VALUE} (M28.7). Errata E28-14 applied: the class
 * is not named {@code LearnedFactor}. A missing or weak value is 0.6 while usable values spread. With fewer than two
 * usable values, or a spread below the minimum, there is no value signal to reorder by (SPIBB): every factor is 1,
 * weak ones included, so the base order stands and having data alone never lifts a candidate. The neutral 0.6 is
 * applied here, not by the table.
 */
public final class LearnedValueConsideration {
    /** Factor of a weak or missing value while usable values spread. */
    public static final double NEUTRAL = 0.6;

    private LearnedValueConsideration() {
    }

    /**
     * Factors in candidate order.
     *
     * @param valueMillis values, one per candidate
     * @param usable whether each cell may move the order
     * @param minSpreadMillis spread below which every factor is 1
     * @return factors in {@code [0.2, 1]}
     */
    public static double[] factors(int[] valueMillis, boolean[] usable, int minSpreadMillis) {
        var low = Integer.MAX_VALUE;
        var high = Integer.MIN_VALUE;
        var count = 0;
        for (var index = 0; index < valueMillis.length; index++) {
            if (!usable[index]) {
                continue;
            }
            count++;
            low = Math.min(low, valueMillis[index]);
            high = Math.max(high, valueMillis[index]);
        }
        var flat = count < 2 || high == low || high - low < minSpreadMillis;
        var factors = new double[valueMillis.length];
        for (var index = 0; index < valueMillis.length; index++) {
            factors[index] = flat ? 1.0 : factor(usable[index], valueMillis[index], low, high);
        }
        return factors;
    }

    /**
     * Score in millis, {@code round(1000 · base · factor)}.
     *
     * @param base normalized base score in {@code [0, 1]}
     * @param factor learned factor
     * @return the score
     */
    public static int scoreMillis(double base, double factor) {
        return (int) Math.round(1000.0 * base * factor);
    }

    private static double factor(boolean usable, int value, int low, int high) {
        if (!usable) {
            return NEUTRAL;
        }
        return 0.2 + 0.8 * (value - low) / (double) (high - low);
    }
}
