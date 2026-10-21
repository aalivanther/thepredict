package akyrion.thepredict.domain;

/**
 * Welford update and Chan merge of {@link OutcomeStat} (M21.5). The same formula is the SQL {@code ON CONFLICT}.
 * Variance is the population second moment {@code m2 / n} for {@code n > 0}, and 0 when empty. Pure.
 */
public final class OutcomeStatMath {
    private OutcomeStatMath() {
    }

    /**
     * Adds one window.
     *
     * @param stat the stat so far
     * @param effect comparative effect of the window
     * @param dealt dealt fraction
     * @param taken taken fraction
     * @param reached whether the movement reached its goal
     * @param mobDied whether the mob died
     * @param targetDied whether the target died
     * @param casts casts of the window
     * @param hits hits of the window
     * @return the updated stat
     */
    public static OutcomeStat add(OutcomeStat stat, double effect, double dealt, double taken, boolean reached,
                                  boolean mobDied, boolean targetDied, int casts, int hits) {
        var n = stat.windows() + 1;
        var delta = effect - stat.effectMean();
        var mean = stat.effectMean() + delta / n;
        var m2 = stat.effectM2() + delta * (effect - mean);
        return new OutcomeStat(n, mean, m2, stat.dealtSum() + dealt, stat.takenSum() + taken,
                stat.reached() + (reached ? 1 : 0), stat.mobDied() + (mobDied ? 1 : 0),
                stat.targetDied() + (targetDied ? 1 : 0), stat.casts() + casts, stat.hits() + hits);
    }

    /**
     * Merges two stats with Chan's formula. An empty side returns the other.
     *
     * @param left one stat
     * @param right the other stat
     * @return the merged stat
     */
    public static OutcomeStat merge(OutcomeStat left, OutcomeStat right) {
        if (left.windows() == 0) {
            return right;
        }
        if (right.windows() == 0) {
            return left;
        }
        var n = left.windows() + right.windows();
        var delta = right.effectMean() - left.effectMean();
        var mean = left.effectMean() + delta * right.windows() / n;
        var m2 = left.effectM2() + right.effectM2() + delta * delta * left.windows() * right.windows() / n;
        return new OutcomeStat(n, mean, m2, left.dealtSum() + right.dealtSum(), left.takenSum() + right.takenSum(),
                left.reached() + right.reached(), left.mobDied() + right.mobDied(),
                left.targetDied() + right.targetDied(), left.casts() + right.casts(), left.hits() + right.hits());
    }

    /**
     * Population variance of the effect.
     *
     * @param stat the stat
     * @return {@code effectM2 / windows}, or 0 when empty
     */
    public static double variance(OutcomeStat stat) {
        return stat.windows() == 0 ? 0.0 : stat.effectM2() / stat.windows();
    }
}
