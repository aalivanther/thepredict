package akyrion.thepredict.domain.distill;

/**
 * One labelled row of a preference tree (M29.2). The label is an outcome or Kev index, never a Jev choice.
 *
 * @param bands band values
 * @param label chosen tactic index
 * @param weight VIPER weight
 */
public record TreeRows(int[] bands, int label, double weight) {
    /**
     * Copies the bands.
     */
    public TreeRows {
        bands = bands.clone();
        if (weight < 0.0) {
            throw new IllegalArgumentException("tree weight");
        }
    }

    /**
     * VIPER weight {@code 1 + min(4, gap / 0.05)}.
     *
     * @param gap value gap of the offered tactics
     * @return the weight
     */
    public static double viper(double gap) {
        var scaled = gap / 0.05;
        if (scaled < 0.0) {
            scaled = 0.0;
        }
        if (scaled > 4.0) {
            scaled = 4.0;
        }
        return 1.0 + scaled;
    }
}
