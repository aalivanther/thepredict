package akyrion.thepredict.domain;

/**
 * The mode a fight actually runs after {@link DifficultyMode#AUTO} is resolved (M22.8).
 */
public enum PlayMode {
    /** Rung fixed at the maximum, no controller. */
    BENCH,
    /** The controller is on. */
    PLAYER
}
