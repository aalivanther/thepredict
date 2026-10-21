package akyrion.thepredict.domain.distill;

/**
 * Greedy regime split (M29.5). A split is accepted only when each side has enough decisions, the out-of-sample
 * regret gain clears the minimum and the bootstrap interval of the gain lies above 0. An interval below 0 says the
 * split makes the regret worse, so it is refused too.
 */
public final class RegimePartitioner {
    private RegimePartitioner() {
    }

    /**
     * Whether one candidate split is kept.
     *
     * @param left decisions on the left
     * @param right decisions on the right
     * @param gain out-of-sample regret reduction
     * @param low bootstrap lower bound
     * @param high bootstrap upper bound
     * @param minGain {@code distill.regime.minGain}
     * @param minDecisions {@code distill.regime.minDecisions}
     * @return {@code true} when the split is accepted
     */
    public static boolean accept(int left, int right, double gain, double low, double high, double minGain,
                                 int minDecisions) {
        if (left < minDecisions || right < minDecisions || gain < minGain) {
            return false;
        }
        return low > 0.0 && high >= low;
    }
}
