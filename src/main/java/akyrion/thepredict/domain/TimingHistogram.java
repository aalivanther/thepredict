package akyrion.thepredict.domain;

/**
 * Reaction times in fixed bins, plus a Welford pair for the mean (M26.3). Measurement only: it is not a vulnerability.
 * The reaction is the first response tick minus the open tick of {@link StimulusKind#TELL_OPEN}.
 */
public final class TimingHistogram {
    private final int binTicks;
    private final int[] bins;
    private int samples;
    private double mean;
    private double m2;

    /**
     * A histogram.
     *
     * @param binTicks width of one bin, 1 to 4
     * @param bins how many bins, at least 1
     */
    public TimingHistogram(int binTicks, int bins) {
        if (binTicks < 1 || bins < 1) {
            throw new IllegalArgumentException("histogram needs a positive bin and at least one slot");
        }
        this.binTicks = binTicks;
        this.bins = new int[bins];
    }

    /**
     * Adds one reaction time.
     *
     * @param ticks non-negative delay
     */
    public void add(int ticks) {
        var slot = ticks / binTicks;
        if (slot >= bins.length) {
            slot = bins.length - 1;
        }
        bins[slot]++;
        samples++;
        var delta = ticks - mean;
        mean += delta / samples;
        m2 += delta * (ticks - mean);
    }

    /**
     * Samples seen.
     *
     * @return the count
     */
    public int samples() {
        return samples;
    }

    /**
     * Welford mean.
     *
     * @return the mean, or 0 when empty
     */
    public double mean() {
        return mean;
    }

    /**
     * Approximate percentile from the bins.
     *
     * @param permille 0 to 1000
     * @return the bin start that reaches that share, or {@code -1} when empty
     */
    public int percentileTicks(int permille) {
        if (samples == 0) {
            return -1;
        }
        var need = (samples * permille + 999) / 1000;
        var seen = 0;
        for (var slot = 0; slot < bins.length; slot++) {
            seen += bins[slot];
            if (seen >= need) {
                return slot * binTicks;
            }
        }
        return (bins.length - 1) * binTicks;
    }
}
