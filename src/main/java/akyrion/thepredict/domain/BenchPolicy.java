package akyrion.thepredict.domain;

/**
 * Fairness against entities (M25). {@link #FREE} records the cast and always grants it.
 */
public enum BenchPolicy {
    /** Record only. Delivered default. */
    FREE,
    /** The same rules as a player. */
    FAIR;

    /** Delivered policy. */
    public static final BenchPolicy DEFAULT = FREE;

    /**
     * Parses the token of {@code Group.AgainstEntities}. Kept for that key (rule W5); another section names its own key
     * with {@link #parse(String, String)}.
     *
     * @param token lower-case token
     * @return the policy
     * @throws IllegalArgumentException when the token is not a policy
     */
    public static BenchPolicy parse(String token) {
        return parse(token, "Group.AgainstEntities");
    }

    /**
     * Parses a configuration token and names the key that held it in the error, so the author finds the section
     * (rule C13): {@code Group.AgainstEntities} or {@code Patterns.AgainstEntities}.
     *
     * @param token lower-case token
     * @param key path of the key, used only in the message
     * @return the policy
     * @throws IllegalArgumentException when the token is not a policy
     */
    public static BenchPolicy parse(String token, String key) {
        return switch (token) {
            case "free" -> FREE;
            case "fair" -> FAIR;
            default -> throw new IllegalArgumentException(key + " must be free or fair, was " + token);
        };
    }
}
