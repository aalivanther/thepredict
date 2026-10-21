package akyrion.thepredict.domain;

/**
 * Entity cadence (M26.8). A constant gap has interquartile range 0 and activates once eight gaps are in.
 */
public final class FixedCadence {
    private FixedCadence() {
    }

    /**
     * Whether the gaps are tight enough.
     *
     * @param gaps ticks between hits, at least eight
     * @return {@code true} when the interquartile range is at most 2
     */
    public static boolean active(int[] gaps) {
        if (gaps.length < 8) {
            return false;
        }
        var sorted = gaps.clone();
        java.util.Arrays.sort(sorted);
        var q1 = sorted[sorted.length / 4];
        var q3 = sorted[(sorted.length * 3) / 4];
        return q3 - q1 <= 2;
    }
}
