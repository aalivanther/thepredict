package akyrion.thepredict.domain;

/**
 * Ten forgotten inter-arrival rates, one per {@link EnemyEpisode} (M26.3, errata E26-4). The prior is
 * {@code Gamma(1, 30 s)} expressed as one event in 600 ticks. Not a vulnerability by itself.
 */
public final class GammaRate {
    /** How many series this table holds. */
    public static final int SERIES = EnemyEpisode.COUNT;
    /** Prior events. */
    public static final double PRIOR_EVENTS = 1.0;
    /** Prior window, 30 seconds. */
    public static final int PRIOR_TICKS = 600;

    private final double[] events = new double[SERIES];
    private final double[] ticks = new double[SERIES];

    /**
     * Rates that still hold only the prior.
     */
    public GammaRate() {
    }

    /**
     * Records a gap for one episode. Allocation-free.
     *
     * @param series episode ordinal, below {@link #SERIES}
     * @param gapTicks ticks since the previous episode of that series, {@code >= 0}
     * @param lambda forgetting in {@code (0, 1]}
     * @throws IllegalArgumentException if an argument is outside its range; the rates are left unchanged
     */
    public void observe(int series, int gapTicks, double lambda) {
        if (series < 0 || series >= SERIES || gapTicks < 0 || !(lambda > 0.0 && lambda <= 1.0)) {
            throw new IllegalArgumentException("gamma rate observation out of range: series " + series + ", gap "
                    + gapTicks + ", lambda " + lambda);
        }
        events[series] = lambda * events[series] + 1.0;
        ticks[series] = lambda * ticks[series] + gapTicks;
    }

    /**
     * Posterior mean gap.
     *
     * @param series episode ordinal
     * @return ticks per event
     */
    public double meanGap(int series) {
        return (PRIOR_TICKS + ticks[series]) / (PRIOR_EVENTS + events[series]);
    }

    /**
     * How many series exist.
     *
     * @return ten
     */
    public int series() {
        return SERIES;
    }
}
