package akyrion.thepredict.engine;

/**
 * The group step of one tick (M25). The default does nothing, which keeps a one-member fight identical to today.
 * It runs after lifecycle and before the tick layer.
 */
public interface GroupControl {
    /** A step that assigns nothing. */
    GroupControl NONE = (tick, budgetNanos) -> {
    };

    /**
     * Releases, arbitrates and replans while the budget lasts.
     *
     * @param tick server tick
     * @param budgetNanos hard cap, plus one replan already started
     */
    void step(long tick, long budgetNanos);
}
