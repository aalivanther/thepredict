package akyrion.thepredict.domain.distill;

/**
 * Weighted Gini of a label column (M29.2).
 */
final class TreeGini {
    private TreeGini() {
    }

    /**
     * Weighted Gini. Empty weight is 0.
     *
     * @param rows the rows
     * @param from inclusive
     * @param to exclusive
     * @param tactics tactic count
     * @return the impurity
     */
    static double gini(TreeRows[] rows, int from, int to, int tactics) {
        var counts = new double[tactics];
        var total = 0.0;
        for (var i = from; i < to; i++) {
            counts[rows[i].label()] += rows[i].weight();
            total += rows[i].weight();
        }
        return impurity(counts, total);
    }

    /**
     * {@code 1 - sum p^2}.
     *
     * @param counts weighted counts
     * @param total total weight
     * @return the impurity
     */
    static double impurity(double[] counts, double total) {
        if (total <= 0.0) {
            return 0.0;
        }
        var sum = 0.0;
        for (var count : counts) {
            var p = count / total;
            sum += p * p;
        }
        return 1.0 - sum;
    }

    /**
     * Decision count, unweighted.
     *
     * @param rows the rows
     * @param from inclusive
     * @param to exclusive
     * @return the count
     */
    static int count(TreeRows[] rows, int from, int to) {
        return to - from;
    }
}
