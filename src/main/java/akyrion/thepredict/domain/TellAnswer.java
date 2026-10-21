package akyrion.thepredict.domain;

/**
 * What the tell asks the player to do (M23.4). There is no {@code NONE}: an absent answer is an empty optional.
 */
public enum TellAnswer {
    /** Raise a shield. */
    BLOCK,
    /** Step aside. */
    DODGE,
    /** Leave the reach. */
    BACK_OFF,
    /** Break line of sight. */
    BREAK_SIGHT,
    /** Hit the caster during the wind-up. Requires poise. */
    INTERRUPT
}
