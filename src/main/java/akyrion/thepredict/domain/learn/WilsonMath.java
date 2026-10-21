package akyrion.thepredict.domain.learn;

/**
 * One-sided 95 percent Wilson lower bound (M28.8). {@code z} is about 1.64485, not the two-sided 1.96.
 */
public final class WilsonMath {
    /** One-sided 95 percent normal quantile. */
    public static final double Z = 1.6448536269514722;

    private WilsonMath() {
    }

    /**
     * Lower bound of a binomial proportion.
     *
     * @param successes successes
     * @param total trials
     * @return the bound in {@code [0, 1]}, or 0 when there are no trials
     */
    public static double lower(int successes, int total) {
        if (total <= 0) {
            return 0.0;
        }
        var proportion = successes / (double) total;
        var z2 = Z * Z;
        var denominator = 1.0 + z2 / total;
        var center = proportion + z2 / (2.0 * total);
        var margin = Z * StrictMath.sqrt(proportion * (1.0 - proportion) / total + z2 / (4.0 * total * total));
        var bound = (center - margin) / denominator;
        if (bound < 0.0) {
            return 0.0;
        }
        return Math.min(bound, 1.0);
    }
}
