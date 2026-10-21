package akyrion.thepredict.adapter.server.patterns;

/**
 * Who counts as having perceived an enemy (M26.1). Visual needs a {@code seenBy} bit and no invisibility. Audible
 * events do not need a line of sight when a living member is inside {@code hearingBlocks}. A received hit always
 * has a witness.
 */
public final class WitnessRule {
    private WitnessRule() {
    }

    /**
     * Whether a visual action was perceived.
     *
     * @param seenByMember some member has the seen-by bit
     * @param invisible the enemy is invisible
     * @return {@code true} when the action is visible
     */
    public static boolean visual(boolean seenByMember, boolean invisible) {
        return seenByMember && !invisible;
    }

    /**
     * Whether an audible event was perceived. The caller compares squared distances of at most 16 members.
     *
     * @param nearestSquared nearest living member, squared blocks
     * @param hearingSquared {@code hearingBlocks} squared
     * @return {@code true} when the sound is in range
     */
    public static boolean audible(double nearestSquared, double hearingSquared) {
        return nearestSquared <= hearingSquared;
    }

    /**
     * A hit received by a member always has a witness.
     *
     * @return {@code true}
     */
    public static boolean receivedHit() {
        return true;
    }
}
