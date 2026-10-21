package akyrion.thepredict.domain.distill;

/**
 * SPIBB override against the local order (M29.2). {@code distill.overrideZ} is this z, not the coverage z.
 * A tree never overrides. Errata E29-5 applied: the label bound uses 1.645, which is not this rule.
 */
public final class ValueOverrideRule {
    /** Level required before an override. */
    public static final int LEVEL = 3;

    private ValueOverrideRule() {
    }

    /**
     * Whether the value leader may replace the local leader.
     *
     * @param usableLevel estimate level
     * @param valueBest lower-safe value of the value leader
     * @param valueLocal value of the local leader
     * @param sdBest standard deviation of the value leader
     * @param sdLocal standard deviation of the local leader
     * @param overrideZ {@code distill.overrideZ}
     * @param tree whether the order came from the tree
     * @return the source to record
     */
    public static DistillSource source(int usableLevel, double valueBest, double valueLocal, double sdBest,
                                       double sdLocal, double overrideZ, boolean tree) {
        if (tree || usableLevel < LEVEL) {
            return DistillSource.LOCAL_ORDER;
        }
        var gap = valueBest - valueLocal;
        var width = overrideZ * StrictMath.sqrt(sdBest * sdBest + sdLocal * sdLocal);
        return gap >= width ? DistillSource.VALUE_OVERRIDE : DistillSource.LOCAL_ORDER;
    }
}
