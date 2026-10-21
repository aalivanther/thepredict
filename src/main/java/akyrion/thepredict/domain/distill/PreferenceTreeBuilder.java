package akyrion.thepredict.domain.distill;

import java.util.Arrays;

/**
 * Deterministic preference tree (M29.2, rule C23). The same rows and limits produce the same tree.
 */
public final class PreferenceTreeBuilder {
    private final int[] cardinalities;
    private final int tactics;
    private final int maxDepth;
    private final int maxLeaves;
    private final int minLeaf;
    private final String[] names;
    private int nextLeaf;
    private int leavesLeft;

    private PreferenceTreeBuilder(int[] cardinalities, int tactics, String[] names, int maxDepth, int maxLeaves,
                                  int minLeaf) {
        this.cardinalities = cardinalities.clone();
        this.tactics = tactics;
        this.names = names.clone();
        this.maxDepth = maxDepth;
        this.maxLeaves = maxLeaves;
        this.minLeaf = minLeaf;
        this.leavesLeft = maxLeaves;
    }

    /**
     * Builds a tree. Rows are copied.
     *
     * @param rows labelled rows
     * @param cardinalities values per band
     * @param tactics tactic count
     * @param names tactic ids
     * @param maxDepth depth cap
     * @param maxLeaves leaf cap
     * @param minLeaf minimum decisions per leaf
     * @return the tree
     */
    public static PreferenceTree build(TreeRows[] rows, int[] cardinalities, int tactics, String[] names,
                                       int maxDepth, int maxLeaves, int minLeaf) {
        var copy = rows.clone();
        var builder = new PreferenceTreeBuilder(cardinalities, tactics, names, maxDepth, maxLeaves, minLeaf);
        return builder.grow(copy, 0, copy.length, 1);
    }

    private PreferenceTree grow(TreeRows[] rows, int from, int to, int depth) {
        if (stop(rows, from, to, depth)) {
            return leaf(rows, from, to);
        }
        var split = TreeSplit.search(rows, from, to, cardinalities, tactics, minLeaf);
        if (split == null || split.gain() <= 0.0 || leavesLeft < 2) {
            return leaf(rows, from, to);
        }
        var mid = partition(rows, from, to, split);
        leavesLeft--;
        var left = grow(rows, from, mid, depth + 1);
        var right = grow(rows, mid, to, depth + 1);
        return new PreferenceTree.Split(split.dimension(), split.mask(), left, right);
    }

    private boolean stop(TreeRows[] rows, int from, int to, int depth) {
        return depth >= maxDepth || to - from < minLeaf * 2 || leavesLeft < 1;
    }

    private PreferenceTree leaf(TreeRows[] rows, int from, int to) {
        var weights = new double[tactics];
        for (var i = from; i < to; i++) {
            weights[rows[i].label()] += rows[i].weight();
        }
        return new PreferenceTree.Leaf(nextLeaf++, order(weights));
    }

    /**
     * Order of a leaf by VIPER-weighted labels. A tactic without a labelled row in the leaf is left out, so a leaf
     * never offers a tactic it has no evidence for.
     */
    private TacticOrder order(double[] weights) {
        var index = new Integer[tactics];
        for (var i = 0; i < tactics; i++) {
            index[i] = i;
        }
        Arrays.sort(index, (a, b) -> {
            var order = Double.compare(weights[b], weights[a]);
            return order != 0 ? order : Integer.compare(a, b);
        });
        var names = new java.util.ArrayList<String>();
        var strengths = new java.util.ArrayList<Integer>();
        var sum = 0.0;
        for (var value : weights) {
            sum += value;
        }
        for (var i = 0; i < tactics && names.size() < TacticOrder.MAX; i++) {
            var tactic = index[i];
            if (!(weights[tactic] > 0.0)) {
                break;
            }
            names.add(this.names[tactic]);
            strengths.add((int) (weights[tactic] * 1000.0 / sum));
        }
        return new TacticOrder(names, strengths, DistillSource.TREE);
    }

    private static int partition(TreeRows[] rows, int from, int to, TreeSplit split) {
        var mid = from;
        for (var i = from; i < to; i++) {
            if ((split.mask() & (1 << rows[i].bands()[split.dimension()])) != 0) {
                var swap = rows[mid];
                rows[mid] = rows[i];
                rows[i] = swap;
                mid++;
            }
        }
        return mid;
    }
}
