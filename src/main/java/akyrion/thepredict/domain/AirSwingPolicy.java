package akyrion.thepredict.domain;

/**
 * What an air swing does (M26.1). {@link #ON} is implemented and tested; the guide keeps it off until DT-75 closes.
 */
public enum AirSwingPolicy {
    /** Diagnostic only. No token and no evidence. */
    OBSERVE,
    /** A clean main-hand weapon swing becomes a token. */
    ON;

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the policy
     * @throws IllegalArgumentException when the token is neither {@code observe} nor {@code on}
     */
    public static AirSwingPolicy parse(String token) {
        return switch (token) {
            case "observe" -> OBSERVE;
            case "on" -> ON;
            default -> throw new IllegalArgumentException("patterns.airSwings must be observe or on, was " + token);
        };
    }
}
