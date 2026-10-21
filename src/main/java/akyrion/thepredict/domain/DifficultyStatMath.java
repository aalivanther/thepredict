package akyrion.thepredict.domain;

/**
 * Merge of one difficulty row (M22.6). Counts sum. The log time-to-kill uses the same Chan formula as
 * {@link OutcomeStatMath}. The start-rung pair is not additive: the later epoch wins, and a tie keeps the incoming
 * pair.
 */
public final class DifficultyStatMath {
    private DifficultyStatMath() {
    }

    /**
     * One merged row of counts and the log time-to-kill.
     *
     * @param fights fights
     * @param mean log mean
     * @param m2 second moment
     * @param censored censored fights
     * @param excluded excluded fights
     * @param startRung start-rung estimate
     * @param startEpochMs epoch of that estimate
     * @param firstEpochMs earliest epoch
     * @param lastEpochMs latest epoch
     */
    public record Row(long fights, double mean, double m2, long censored, long excluded, double startRung,
                      long startEpochMs, long firstEpochMs, long lastEpochMs) {
    }

    /**
     * Merges two rows.
     *
     * @param left the stored row
     * @param right the incoming row
     * @return the merged row
     */
    public static Row merge(Row left, Row right) {
        var chan = chan(left.fights, left.mean, left.m2, right.fights, right.mean, right.m2);
        var incoming = right.startEpochMs >= left.startEpochMs;
        return new Row(left.fights + right.fights, chan[0], chan[1], left.censored + right.censored,
                left.excluded + right.excluded, incoming ? right.startRung : left.startRung,
                incoming ? right.startEpochMs : left.startEpochMs, Math.min(left.firstEpochMs, right.firstEpochMs),
                Math.max(left.lastEpochMs, right.lastEpochMs));
    }

    /**
     * Chan merge of a mean, checked against {@link OutcomeStatMath} by mapping fights onto windows.
     *
     * @param leftFights left count
     * @param leftMean left mean
     * @param leftM2 left moment
     * @param rightFights right count
     * @param rightMean right mean
     * @param rightM2 right moment
     * @return mean and moment
     */
    public static double[] chan(long leftFights, double leftMean, double leftM2, long rightFights, double rightMean,
                                double rightM2) {
        var left = new OutcomeStat(leftFights, leftMean, leftM2, 0, 0, 0, 0, 0, 0, 0);
        var right = new OutcomeStat(rightFights, rightMean, rightM2, 0, 0, 0, 0, 0, 0, 0);
        var merged = OutcomeStatMath.merge(left, right);
        return new double[] {merged.effectMean(), merged.effectM2()};
    }
}
