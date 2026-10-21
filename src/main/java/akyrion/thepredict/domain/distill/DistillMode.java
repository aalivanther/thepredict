package akyrion.thepredict.domain.distill;

/**
 * How far a distilled policy may act (M29.4). Order is {@code off &lt; shadow &lt; assist &lt; autonomous}.
 * A profile may only lower the global mode.
 */
public enum DistillMode {
    /** The capture path is unchanged. */
    OFF,
    /** The student is recorded and never decides. */
    SHADOW,
    /** Covered cells decide, except the audit sample. */
    ASSIST,
    /** Covered cells decide with no audit. Out of coverage follows {@code jevForOod}. */
    AUTONOMOUS;

    /**
     * Rank used to take the minimum of configured, verdict and demotion.
     *
     * @return 0 for off through 3 for autonomous
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
    public DistillMode lower(DistillMode other) {
        return rank() <= other.rank() ? this : other;
    }

    /**
     * Parses a config token.
     *
     * @param token the token
     * @return the mode
     * @throws IllegalArgumentException if the token is unknown
     */
    public static DistillMode parse(String token) {
        return switch (token) {
            case "off" -> OFF;
            case "shadow" -> SHADOW;
            case "assist" -> ASSIST;
            case "autonomous" -> AUTONOMOUS;
            default -> throw new IllegalArgumentException("distill mode");
        };
    }
}
