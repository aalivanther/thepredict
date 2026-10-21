package akyrion.thepredict.domain.distill;

/**
 * Deterministic tree baseline (M29.0). The same rows produce the same path. No seed and no Jev label.
 */
public final class DistillMethodsBaseline {
    private DistillMethodsBaseline() {
    }

    /**
     * The inspect path of one tree, built twice from the same rows.
     *
     * @param rows outcome rows
     * @param bands the query
     * @return the path
     */
    public static String path(TreeRows[] rows, int[] bands) {
        var names = new String[] {"t0", "t1"};
        var cards = new int[] {4, 3, 3, 2, 4, 3, 7};
        var first = PreferenceTreeBuilder.build(rows, cards, 2, names, 6, 64, 10);
        var second = PreferenceTreeBuilder.build(rows, cards, 2, names, 6, 64, 10);
        var path = first.path(bands);
        if (!path.equals(second.path(bands))) {
            throw new IllegalStateException("tree path");
        }
        return path;
    }
}
