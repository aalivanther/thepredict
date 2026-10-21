package akyrion.thepredict.domain;

/**
 * How long one reserved choice may keep a token (M26.5). A survival role is never reserved. Giving the token back
 * uses {@link TokenEnd#YIELDED}, which is not an impact.
 */
public final class ReservationHold {
    /** Longest hold, in ticks. */
    public static final int MAX_TICKS = 40;
    private static final int TOKEN_SLACK = 5;

    private ReservationHold() {
    }

    /**
     * Whether this role may be held back.
     *
     * @param role the choice role
     * @return {@code true} for interrupt, gap close and finisher
     */
    public static boolean mayHold(SkillRole role) {
        return role == SkillRole.INTERRUPT || role == SkillRole.GAP_CLOSE || role == SkillRole.FINISHER;
    }

    /**
     * Melee-token limit. Five ticks stay for the idle timeout.
     *
     * @param idleTimeoutTicks group idle timeout
     * @return the hold limit
     */
    public static int meleeLimit(int idleTimeoutTicks) {
        return idleTimeoutTicks - TOKEN_SLACK;
    }

    /**
     * Ends a hold that has run out. The end is never {@link TokenEnd#RELEASED}.
     *
     * @param heldTicks ticks the choice has been held
     * @param idleTimeoutTicks group idle timeout
     * @return a yield, or empty while the hold may continue
     */
    public static java.util.Optional<TokenEnd> giveBack(int heldTicks, int idleTimeoutTicks) {
        if (heldTicks >= meleeLimit(idleTimeoutTicks) || heldTicks >= MAX_TICKS) {
            return java.util.Optional.of(TokenEnd.YIELDED);
        }
        return java.util.Optional.empty();
    }
}
