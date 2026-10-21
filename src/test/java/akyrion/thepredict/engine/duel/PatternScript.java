package akyrion.thepredict.engine.duel;

/**
 * One labelled script layer (M26.10). Only {@code PatternEval} reads the label.
 */
public final class PatternScript {
    private final String label;
    private final double probability;

    /**
     * A layer that shows its action with a fixed probability.
     *
     * @param label ground-truth name
     * @param probability probability in {@code [0, 1]}
     */
    public PatternScript(String label, double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability");
        }
        this.label = label;
        this.probability = probability;
    }

    /**
     * The ground-truth label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Whether this draw shows the scripted action.
     *
     * @param draw a mixed random word
     * @return the Bernoulli sample
     */
    public boolean shows(long draw) {
        var unit = (draw >>> 11) / (double) (1L << 53);
        return unit < probability;
    }

    /**
     * The scripted probability.
     *
     * @return the probability
     */
    public double probability() {
        return probability;
    }
}
