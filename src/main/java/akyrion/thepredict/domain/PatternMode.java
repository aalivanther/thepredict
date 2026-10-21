package akyrion.thepredict.domain;

/**
 * How far pattern detection may affect a fight (M26.6). A profile may only lower the global mode.
 */
public enum PatternMode {
    /** No observation. */
    OFF,
    /** Detect, record and summarize. Delivery default. Decisions stay unchanged. */
    OBSERVE,
    /** Local bias inside A14, after the gate. */
    BIAS,
    /** Bias plus fixed phrases to Jev, after the gate and G2. */
    FULL;

    /** Delivered mode. */
    public static final PatternMode DEFAULT = OBSERVE;

    /**
     * Rank used to take the minimum of two modes.
     *
     * @return the ordinal
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
    public PatternMode min(PatternMode other) {
        return rank() <= other.rank() ? this : other;
    }

    /**
     * Parses a configuration token.
     *
     * @param token lower-case token
     * @return the mode
     * @throws IllegalArgumentException when the token is not a mode
     */
    public static PatternMode parse(String token) {
        return switch (token) {
            case "off" -> OFF;
            case "observe" -> OBSERVE;
            case "bias" -> BIAS;
            case "full" -> FULL;
            default -> throw new IllegalArgumentException(
                    "patterns mode must be off, observe, bias or full, was " + token);
        };
    }
}
