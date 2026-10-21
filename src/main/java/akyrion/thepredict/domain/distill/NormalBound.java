package akyrion.thepredict.domain.distill;

/**
 * One-sided normal lower bound of a mean (M29.3, errata E29-1 applied). Computed on the server from
 * {@code n}, {@code sumDelta} and {@code sumDeltaSq}. The z of 1.64485 is the 95 percent one-sided normal quantile.
 */
public final class NormalBound {
    /** One-sided 95 percent normal quantile. */
    public static final double Z95 = 1.6448536269514722;

    private NormalBound() {
    }

    /**
     * Sample mean.
     *
     * @param n count
     * @param sumDelta sum of differences
     * @return the mean, or 0 when {@code n} is 0
     */
    public static double mean(int n, double sumDelta) {
        return n <= 0 ? 0.0 : sumDelta / n;
    }

    /**
     * Sample variance. Zero when fewer than two observations.
     *
     * @param n count
     * @param sumDelta sum of differences
     * @param sumDeltaSq sum of squares
     * @return the variance
     */
    public static double variance(int n, double sumDelta, double sumDeltaSq) {
        if (n <= 1) {
            return 0.0;
        }
        var raw = (sumDeltaSq - sumDelta * sumDelta / n) / (n - 1);
        return raw < 0.0 ? 0.0 : raw;
    }

    /**
     * Lower bound {@code mean − z · se}.
     *
     * @param n count
     * @param sumDelta sum of differences
     * @param sumDeltaSq sum of squares
     * @return the bound
     */
    public static double lower(int n, double sumDelta, double sumDeltaSq) {
        if (n <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        var se = StrictMath.sqrt(variance(n, sumDelta, sumDeltaSq) / n);
        return mean(n, sumDelta) - Z95 * se;
    }
}
