package akyrion.thepredict.domain;

/**
 * When {@code g0} is attached (M25). {@link #ON_CHANGE} is the delivered value.
 */
public enum PlanQuestion {
    /** Only when the signature changed or the plan is older than the horizon. */
    ON_CHANGE,
    /** On every heartbeat that may ask. */
    EVERY_HEARTBEAT;

    /** Delivered timing. */
    public static final PlanQuestion DEFAULT = ON_CHANGE;

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the timing
     * @throws IllegalArgumentException when the token is not a timing
     */
    public static PlanQuestion parse(String token) {
        return switch (token) {
            case "on_change" -> ON_CHANGE;
            case "every_heartbeat" -> EVERY_HEARTBEAT;
            default -> throw new IllegalArgumentException(
                    "Group.PlanQuestion must be on_change or every_heartbeat, was " + token);
        };
    }
}
