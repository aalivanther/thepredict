package akyrion.thepredict.domain;

/**
 * How far a group may coordinate (M25). Order is the rank: a profile may only lower the global mode.
 */
public enum GroupMode {
    /** No group step and no fairness gate. */
    OFF,
    /** Fairness against players. Delivery default. */
    FAIR,
    /** Fairness plus roles and slots. */
    COORDINATE,
    /** Coordinate, plus a group question when the profile asks one. */
    FULL;

    /** Delivered mode. */
    public static final GroupMode DEFAULT = FAIR;

    /**
     * Rank used to take the minimum of two modes.
     *
     * @return the ordinal, which is the rank
     */
    public int rank() {
        return ordinal();
    }

    /**
     * The lower of two modes.
     *
     * @param other the other mode
     * @return the mode with the smaller rank
     */
    public GroupMode min(GroupMode other) {
        return rank() <= other.rank() ? this : other;
    }

    /**
     * The higher of two modes.
     *
     * @param other the other mode
     * @return the mode with the larger rank
     */
    public GroupMode max(GroupMode other) {
        return rank() >= other.rank() ? this : other;
    }

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the mode
     * @throws IllegalArgumentException when the token is not a mode
     */
    public static GroupMode parse(String token) {
        return switch (token) {
            case "off" -> OFF;
            case "fair" -> FAIR;
            case "coordinate" -> COORDINATE;
            case "full" -> FULL;
            default -> throw new IllegalArgumentException(
                    "group mode must be off, fair, coordinate or full, was " + token);
        };
    }
}
