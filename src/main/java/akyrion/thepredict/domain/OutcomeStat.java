package akyrion.thepredict.domain;

/**
 * Aggregated outcome windows of one key (M21.5). {@code effectMean} and {@code effectM2} are Welford's mean and
 * centered sum of squares. Immutable.
 *
 * @param windows windows merged into this stat, {@code >= 0}
 * @param effectMean mean of the comparative effect
 * @param effectM2 centered sum of squares of the effect
 * @param dealtSum sum of dealt fractions
 * @param takenSum sum of taken fractions
 * @param reached windows that reached their movement
 * @param mobDied windows in which the mob died
 * @param targetDied windows in which the target died
 * @param casts casts inside the windows
 * @param hits confirmed hits inside the windows
 */
public record OutcomeStat(long windows, double effectMean, double effectM2, double dealtSum, double takenSum,
                          long reached, long mobDied, long targetDied, long casts, long hits) {
    /** A stat with nothing observed. */
    public static final OutcomeStat EMPTY = new OutcomeStat(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    /**
     * Validates the counts.
     *
     * @throws IllegalArgumentException if a count is negative or a moment is not finite
     */
    public OutcomeStat {
        if (windows < 0 || reached < 0 || mobDied < 0 || targetDied < 0 || casts < 0 || hits < 0) {
            throw new IllegalArgumentException("outcome stat counts must be >= 0");
        }
        if (!Double.isFinite(effectMean) || !Double.isFinite(effectM2) || !Double.isFinite(dealtSum)
                || !Double.isFinite(takenSum)) {
            throw new IllegalArgumentException("outcome stat moments must be finite");
        }
    }
}
