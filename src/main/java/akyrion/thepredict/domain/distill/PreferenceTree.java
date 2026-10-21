package akyrion.thepredict.domain.distill;

import java.util.List;
import java.util.Optional;

/**
 * A preference tree (M29.2). Splits test whether band {@code dimension} is in a bit mask. Leaves are numbered
 * in preorder.
 */
public sealed interface PreferenceTree permits PreferenceTree.Split, PreferenceTree.Leaf {
    /**
     * A split.
     *
     * @param dimension band index
     * @param mask bits of the values that go left
     * @param left left child
     * @param right right child
     */
    record Split(int dimension, int mask, PreferenceTree left, PreferenceTree right) implements PreferenceTree {
    }

    /**
     * A leaf.
     *
     * @param id preorder id
     * @param order tactic order
     */
    record Leaf(int id, TacticOrder order) implements PreferenceTree {
    }

    /**
     * Walks to a leaf.
     *
     * @param bands band values
     * @return the leaf
     */
    default Leaf leaf(int[] bands) {
        return switch (this) {
            case Leaf leaf -> leaf;
            case Split split -> (split.mask() & (1 << bands[split.dimension()])) != 0
                    ? split.left().leaf(bands) : split.right().leaf(bands);
        };
    }

    /**
     * Counts leaves.
     *
     * @return the count
     */
    default int leaves() {
        return switch (this) {
            case Leaf ignored -> 1;
            case Split split -> split.left().leaves() + split.right().leaves();
        };
    }

    /**
     * Depth.
     *
     * @return the depth
     */
    default int depth() {
        return switch (this) {
            case Leaf ignored -> 1;
            case Split split -> 1 + Math.max(split.left().depth(), split.right().depth());
        };
    }

    /**
     * Text of the path for inspect.
     *
     * @param bands band values
     * @return the path
     */
    default String path(int[] bands) {
        var text = new StringBuilder();
        PreferenceTree node = this;
        while (node instanceof Split split) {
            text.append('d').append(split.dimension()).append(':').append(split.mask()).append('/');
            node = (split.mask() & (1 << bands[split.dimension()])) != 0 ? split.left() : split.right();
        }
        text.append("leaf").append(((Leaf) node).id());
        return text.toString();
    }

    /**
     * A single leaf.
     *
     * @param order the order
     * @return the tree
     */
    static PreferenceTree leaf(TacticOrder order) {
        return new Leaf(0, order);
    }

    /**
     * Whether every leaf order has at most eight tactics.
     *
     * @param tree the tree
     * @return {@code true} when the cap holds
     */
    static boolean capped(PreferenceTree tree) {
        return switch (tree) {
            case Leaf leaf -> leaf.order().tactics().size() <= TacticOrder.MAX;
            case Split split -> capped(split.left()) && capped(split.right());
        };
    }

    /**
     * Leaf ids in preorder.
     *
     * @param tree the tree
     * @param into the destination
     */
    static void ids(PreferenceTree tree, List<Integer> into) {
        switch (tree) {
            case Leaf leaf -> into.add(leaf.id());
            case Split split -> {
                ids(split.left(), into);
                ids(split.right(), into);
            }
        }
    }

    /**
     * The order chosen for these bands, if a candidate matches.
     *
     * @param bands band values
     * @param candidates live candidates
     * @return the tactic
     */
    default Optional<String> choose(int[] bands, List<String> candidates) {
        return leaf(bands).order().firstAmong(candidates);
    }
}
