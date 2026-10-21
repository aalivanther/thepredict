package akyrion.thepredict.domain;

/**
 * Bits of {@link Reaction} by ordinal (M22.7). The five reactions that stay on every rung ({@code EVADE_CRITICAL},
 * {@code DODGE_SHOT}, {@code SPACE_OUT}, {@code RESPECT_SHIELD} and {@code EDGE_DANGER}) are always admitted.
 */
public final class ReactionMask {
    /** Every reaction. */
    public static final long ALL = (1L << Reaction.values().length) - 1L;

    private static final long KEPT = bit(Reaction.EVADE_CRITICAL) | bit(Reaction.DODGE_SHOT)
            | bit(Reaction.SPACE_OUT) | bit(Reaction.RESPECT_SHIELD) | bit(Reaction.EDGE_DANGER);

    private ReactionMask() {
    }

    /**
     * The bit of one reaction.
     *
     * @param reaction the reaction
     * @return the bit
     */
    public static long bit(Reaction reaction) {
        return 1L << reaction.ordinal();
    }

    /**
     * Whether a mask admits a reaction. The kept reactions are admitted even when the bit is clear.
     *
     * @param mask the lever mask
     * @param reaction the reaction
     * @return whether it may arm
     */
    public static boolean admits(long mask, Reaction reaction) {
        var bit = bit(reaction);
        return (KEPT & bit) != 0 || (mask & bit) != 0;
    }

    /**
     * A mask with the named reactions removed and the kept reactions forced on.
     *
     * @param removed reactions the rung drops
     * @return the mask
     */
    public static long without(Reaction... removed) {
        var mask = ALL;
        for (var reaction : removed) {
            mask &= ~bit(reaction);
        }
        return mask | KEPT;
    }
}
