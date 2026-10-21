package akyrion.thepredict.domain;

/**
 * Whether a mob may prefer a cast that pushes an entity into a hazard (M24.6). Never a player.
 */
public enum HazardPush {
    /** The reaction stays off. */
    OFF,
    /** Against an entity, and only in guard or full, a lethal push may be preferred. */
    EXPLOIT;

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the setting
     * @throws IllegalArgumentException when the token is unknown
     */
    public static HazardPush parse(String token) {
        return switch (token) {
            case "off" -> OFF;
            case "exploit" -> EXPLOIT;
            default -> throw new IllegalArgumentException("Zone.HazardPush must be off or exploit, was " + token);
        };
    }
}
