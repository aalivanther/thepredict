package akyrion.thepredict.domain;

/**
 * What the group does when the leader dies (M25).
 */
public enum LeaderDeath {
    /** A successor takes the lead at once. */
    SUCCEED,
    /** The group stays broken for {@code group.brokenTicks}, then succeeds. */
    BREAK;

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the policy
     * @throws IllegalArgumentException when the token is not a policy
     */
    public static LeaderDeath parse(String token) {
        return switch (token) {
            case "succeed" -> SUCCEED;
            case "break" -> BREAK;
            default -> throw new IllegalArgumentException("Group.OnLeaderDeath must be succeed or break, was " + token);
        };
    }
}
