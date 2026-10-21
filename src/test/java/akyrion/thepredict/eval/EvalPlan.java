package akyrion.thepredict.eval;

import akyrion.thepredict.engine.RequestBudget;

/**
 * How many paired calls an offline run may send (M22.4, W7). Pair {@code n + 1} is not sent. The estimate uses the
 * single token price.
 */
public final class EvalPlan {
    private final int pairs;

    /**
     * @param pairs pairs to send, {@code 1} to {@code 800}
     */
    public EvalPlan(int pairs) {
        if (pairs < 1 || pairs > EvalStats.MAX_PAIRS) {
            throw new IllegalArgumentException("pairs");
        }
        this.pairs = pairs;
    }

    /**
     * Whether pair {@code index} is still inside the plan.
     *
     * @param index zero-based pair
     * @return {@code false} for {@code n} and beyond
     */
    public boolean maySend(int index) {
        return index >= 0 && index < pairs;
    }

    /**
     * Dollar estimate of one body at the initial characters per token.
     *
     * @param bodyChars characters
     * @return tokens times the single price
     */
    public static double estimateUsd(int bodyChars) {
        var tokens = (int) Math.ceil(bodyChars / RequestBudget.INITIAL_CHARS_PER_TOKEN);
        return tokens * RequestBudget.USD_PER_MILLION_INPUT_TOKENS / 1_000_000.0;
    }

    /**
     * Whether another call still fits the cap.
     *
     * @param spent dollars already spent
     * @param estimate dollars of the next call
     * @param maxUsd cap
     * @return {@code false} when the sum would pass the cap, or a figure is negative
     */
    public static boolean withinCap(double spent, double estimate, double maxUsd) {
        if (spent < 0.0 || estimate < 0.0 || maxUsd < 0.0) {
            return false;
        }
        return spent + estimate <= maxUsd;
    }
}
