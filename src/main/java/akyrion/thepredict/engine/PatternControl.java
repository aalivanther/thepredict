package akyrion.thepredict.engine;

/**
 * The pattern step of one tick (M26). The default does nothing, so a fight with patterns off matches today.
 * It runs after the group step and before the tick layer.
 */
public interface PatternControl {
    /** A step that observes nothing. */
    PatternControl NONE = (tick, budgetNanos) -> {
    };

    /**
     * Samples, tokens and detectors while the budget lasts.
     *
     * @param tick server tick
     * @param budgetNanos hard cap of the step
     */
    void step(long tick, long budgetNanos);
}
