package akyrion.thepredict.domain;

/**
 * How far a zone may change a decision (M24.1). Order is the rank: a profile may only lower the global mode.
 */
public enum ZoneMode {
    /** No zone is built and no decision reads terrain. */
    OFF,
    /** The zone is built. Against a player it also applies the push policy and tell feasibility. */
    OBSERVE,
    /** Observe, plus the mob refuses unsafe ground. */
    GUARD,
    /** Guard, plus terrain facts may reach Jev and the signature. */
    FULL;

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
    public ZoneMode min(ZoneMode other) {
        return rank() <= other.rank() ? this : other;
    }

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the mode
     * @throws IllegalArgumentException when the token is not a mode
     */
    public static ZoneMode parse(String token) {
        return switch (token) {
            case "off" -> OFF;
            case "observe" -> OBSERVE;
            case "guard" -> GUARD;
            case "full" -> FULL;
            default -> throw new IllegalArgumentException("zone mode must be off, observe, guard or full, was " + token);
        };
    }
}
