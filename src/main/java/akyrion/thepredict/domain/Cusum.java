package akyrion.thepredict.domain;

/**
 * Bernoulli CUSUM that watches a drop (M26.3). An alarm resets the statistic and never changes the pattern state by
 * itself. The caller halves the evidence.
 */
public final class Cusum {
    private double statistic;
    private double baseline = Double.NaN;

    /**
     * A chart that is not armed yet.
     */
    public Cusum() {
    }

    /**
     * Arms the chart at the mean observed when the pattern activated.
     *
     * @param baselineMean {@code p0} in {@code (0, 1)}
     */
    public void arm(double baselineMean) {
        baseline = baselineMean;
        statistic = 0.0;
    }

    /**
     * Clears the chart.
     */
    public void reset() {
        baseline = Double.NaN;
        statistic = 0.0;
    }

    /**
     * One observation. Failures raise the statistic when {@code p1 < p0}.
     *
     * @param success whether the pattern was shown
     * @param alternative {@code p1}, normally {@code theta - 0.1}
     * @param threshold {@code h}
     * @return whether {@code g} crossed {@code h}; the statistic is then zero
     */
    public boolean observe(boolean success, double alternative, double threshold) {
        if (!(baseline > 0.0) || !(baseline < 1.0) || !(alternative > 0.0) || !(alternative < 1.0)) {
            return false;
        }
        var rise = StrictMath.log(alternative / baseline);
        var fall = StrictMath.log((1.0 - alternative) / (1.0 - baseline));
        var step = success ? rise : fall;
        statistic = statistic + step > 0.0 ? statistic + step : 0.0;
        if (statistic > threshold) {
            statistic = 0.0;
            return true;
        }
        return false;
    }

    /**
     * The current statistic.
     *
     * @return {@code g}
     */
    public double statistic() {
        return statistic;
    }
}
