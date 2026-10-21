package akyrion.thepredict.domain;

/**
 * How a plan spreads targets (M25). Against players the main target is the highest shared threat.
 */
public enum TargetDistribution {
    /** One main target. */
    FOCUS,
    /** Several targets. */
    SPREAD;

    /**
     * Distribution of a plan. Split plans spread. Every other plan focuses.
     *
     * @param plan the adopted plan
     * @return the distribution
     */
    public static TargetDistribution of(GroupPlan plan) {
        return plan == GroupPlan.SPLIT_TARGETS ? SPREAD : FOCUS;
    }

    /**
     * Index of the greatest shared threat. Ties keep the lowest index.
     *
     * @param shared summed threat per participant
     * @param count how many entries are filled
     * @return the index, or -1 when {@code count} is 0
     */
    public static int focus(double[] shared, int count) {
        if (shared == null || count <= 0) {
            return -1;
        }
        var best = 0;
        var limit = Math.min(count, shared.length);
        for (var index = 1; index < limit; index++) {
            if (shared[index] > shared[best]) {
                best = index;
            }
        }
        return best;
    }

    /**
     * Index of the weakest known health. An unknown band is {@link Integer#MAX_VALUE} and is skipped.
     * Ties keep the lowest index.
     *
     * @param healthOrdinal health band ordinal per participant
     * @param count how many entries are filled
     * @return the index, or -1 when every band is unknown
     */
    public static int weakest(int[] healthOrdinal, int count) {
        if (healthOrdinal == null || count <= 0) {
            return -1;
        }
        var best = -1;
        var low = Integer.MAX_VALUE;
        var limit = Math.min(count, healthOrdinal.length);
        for (var index = 0; index < limit; index++) {
            if (healthOrdinal[index] < low) {
                low = healthOrdinal[index];
                best = index;
            }
        }
        return low == Integer.MAX_VALUE ? -1 : best;
    }
}
