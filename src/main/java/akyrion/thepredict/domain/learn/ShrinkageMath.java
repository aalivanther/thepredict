package akyrion.thepredict.domain.learn;

/**
 * Hierarchical shrinkage (M28.3). The root prior is the profile's global mean reward, not {@code LocalPlanner.score}
 * (errata E28-4 applied). Pure. {@link StrictMath} only.
 */
public final class ShrinkageMath {
    /** Lowest κ from the X4 band. */
    public static final double KAPPA_MIN = 4.0;
    /** Highest κ from the X4 band. */
    public static final double KAPPA_MAX = 100.0;
    /** Floor of the between-cell variance. */
    public static final double TAU_FLOOR = 1.0e-4;

    private ShrinkageMath() {
    }

    /**
     * κ = clamp(within / between, 4, 100). The between term is already floored.
     *
     * @param within pooled within-cell variance
     * @param between between-cell variance
     * @return κ
     */
    public static double kappa(double within, double between) {
        var tau = Math.max(between, TAU_FLOOR);
        var raw = within / tau;
        if (raw < KAPPA_MIN) {
            return KAPPA_MIN;
        }
        return Math.min(raw, KAPPA_MAX);
    }

    /**
     * Posterior mean {@code (n·m + κ·parent) / (n+κ)}.
     *
     * @param count windows
     * @param mean cell mean
     * @param kappa shrinkage
     * @param parent parent estimate
     * @return the shrunk mean
     */
    public static double shrink(double count, double mean, double kappa, double parent) {
        return (count * mean + kappa * parent) / (count + kappa);
    }

    /**
     * Posterior standard deviation {@code sqrt(within / (n+κ))}.
     *
     * @param within within-cell variance
     * @param count windows
     * @param kappa shrinkage
     * @return the deviation
     */
    public static double deviation(double within, double count, double kappa) {
        return StrictMath.sqrt(Math.max(within, 0.0) / (count + kappa));
    }

    /**
     * Week weight {@code 2^(-7k / halfLifeDays)}, oldest weeks first when the caller sorts them.
     *
     * @param ageWeeks weeks before the build week
     * @param halfLifeDays half life
     * @return the weight
     */
    public static double weekWeight(int ageWeeks, int halfLifeDays) {
        return StrictMath.pow(2.0, -7.0 * ageWeeks / halfLifeDays);
    }

    /**
     * Between-cell variance: variance of the child means minus the mean sampling variance, floored at 1e-4.
     *
     * @param means child means
     * @param counts child counts
     * @param within pooled within variance
     * @return τ²
     */
    public static double between(double[] means, double[] counts, double within) {
        if (means.length == 0) {
            return TAU_FLOOR;
        }
        var center = meanOf(means);
        var spread = 0.0;
        var sampling = 0.0;
        for (var index = 0; index < means.length; index++) {
            var gap = means[index] - center;
            spread += gap * gap;
            var count = counts[index] <= 0.0 ? 1.0 : counts[index];
            sampling += within / count;
        }
        var tau = spread / means.length - sampling / means.length;
        return Math.max(tau, TAU_FLOOR);
    }

    /**
     * Thousandths of a reward, half away from zero via {@link Math#round(double)}.
     *
     * @param value the reward
     * @return millis
     */
    public static int millis(double value) {
        return (int) Math.round(value * 1000.0);
    }

    private static double meanOf(double[] means) {
        var sum = 0.0;
        for (var mean : means) {
            sum += mean;
        }
        return sum / means.length;
    }
}
