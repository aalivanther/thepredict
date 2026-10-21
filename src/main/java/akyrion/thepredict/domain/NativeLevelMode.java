package akyrion.thepredict.domain;

/**
 * Whether a profile may ask the next spawn to move the MythicMobs level by one (M22.12). Never during a fight.
 */
public enum NativeLevelMode {
    /** The level stays where the author put it. */
    OFF,
    /** The next spawn may move one step inside the author's band after an unreachable verdict. */
    SPAWN;

    /** Delivery default. */
    public static final NativeLevelMode DEFAULT = OFF;

    /**
     * Parses a profile token.
     *
     * @param text {@code off} or {@code spawn}
     * @return the mode
     * @throws IllegalArgumentException if the token is neither
     */
    public static NativeLevelMode parse(String text) {
        return switch (text) {
            case "off" -> OFF;
            case "spawn" -> SPAWN;
            default -> throw new IllegalArgumentException("native level");
        };
    }
}
