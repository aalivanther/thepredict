package akyrion.thepredict.domain.distill;

/**
 * One candidate split (M29.2). Ties break by higher gain, then smaller dimension, then smaller mask.
 *
 * @param dimension band index
 * @param mask left-hand bits
 * @param gain impurity reduction
 * @param left count on the left
 */
record TreeSplit(int dimension, int mask, double gain, int left) {
    /**
     * Whether this split beats the current best.
     *
     * @param best the best so far, or empty
     * @return {@code true} when this split wins
     */
    boolean betterThan(TreeSplit best) {
        if (best == null) {
            return true;
        }
        if (gain > best.gain) {
            return true;
        }
        if (gain < best.gain) {
            return false;
        }
        if (dimension != best.dimension) {
            return dimension < best.dimension;
        }
        return mask < best.mask;
    }

    /**
     * Searches every proper mask of every dimension.
     *
     * @param rows rows of one node
     * @param from inclusive
     * @param to exclusive
     * @param cardinalities values per dimension
     * @param tactics tactic count
     * @param minLeaf minimum decisions on each side
     * @return the best split, or empty
     */
    static TreeSplit search(TreeRows[] rows, int from, int to, int[] cardinalities, int tactics, int minLeaf) {
        TreeSplit best = null;
        var parent = TreeGini.gini(rows, from, to, tactics);
        for (var dimension = 0; dimension < cardinalities.length; dimension++) {
            best = scan(rows, from, to, dimension, cardinalities[dimension], tactics, minLeaf, parent, best);
        }
        return best;
    }

    /**
     * A mask and its complement make the same partition with the sides swapped and the same gain, and the tie break
     * keeps the smaller mask, so only masks below their complement are scanned: each partition once.
     */
    private static TreeSplit scan(TreeRows[] rows, int from, int to, int dimension, int cardinality, int tactics,
                                  int minLeaf, double parent, TreeSplit best) {
        var full = (1 << cardinality) - 1;
        for (var mask = 1; mask < full - mask; mask++) {
            var left = side(rows, from, to, dimension, mask);
            if (left < minLeaf || to - from - left < minLeaf) {
                continue;
            }
            var gain = parent - child(rows, from, to, dimension, mask, tactics);
            var split = new TreeSplit(dimension, mask, gain, left);
            if (split.betterThan(best)) {
                best = split;
            }
        }
        return best;
    }

    private static int side(TreeRows[] rows, int from, int to, int dimension, int mask) {
        var left = 0;
        for (var i = from; i < to; i++) {
            if ((mask & (1 << rows[i].bands()[dimension])) != 0) {
                left++;
            }
        }
        return left;
    }

    private static double child(TreeRows[] rows, int from, int to, int dimension, int mask, int tactics) {
        var left = new double[tactics];
        var right = new double[tactics];
        var leftWeight = 0.0;
        var rightWeight = 0.0;
        for (var i = from; i < to; i++) {
            var row = rows[i];
            if ((mask & (1 << row.bands()[dimension])) != 0) {
                left[row.label()] += row.weight();
                leftWeight += row.weight();
            } else {
                right[row.label()] += row.weight();
                rightWeight += row.weight();
            }
        }
        var total = leftWeight + rightWeight;
        if (total <= 0.0) {
            return 0.0;
        }
        return (leftWeight * TreeGini.impurity(left, leftWeight) + rightWeight * TreeGini.impurity(right, rightWeight))
                / total;
    }
}
