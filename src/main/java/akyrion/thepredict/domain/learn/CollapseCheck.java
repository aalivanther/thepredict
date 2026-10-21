package akyrion.thepredict.domain.learn;

/**
 * Collapse of the greedy policy (M28.3). More than 80 percent of the window-weighted greedy choices on one tactic
 * is {@link LearnCause#COLLAPSE_RISK}. It does not block activation.
 */
public final class CollapseCheck {
    /** Share that raises the risk. */
    public static final double LIMIT = 0.80;

    private CollapseCheck() {
    }

    /**
     * Whether one tactic dominates.
     *
     * @param weights window weights per tactic, same order as the caller
     * @return {@code true} when the heaviest share is above {@link #LIMIT}
     */
    public static boolean collapsed(double[] weights) {
        var total = 0.0;
        var best = 0.0;
        for (var weight : weights) {
            total += weight;
            best = Math.max(best, weight);
        }
        return total > 0.0 && best / total > LIMIT;
    }
}
