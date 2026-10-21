package akyrion.thepredict.domain;

/**
 * How a mob chooses its pressure (M22.8). {@link #AUTO} plays {@link #PLAYER} against players and {@link #BENCH}
 * against every other target. One default, here.
 */
public enum DifficultyMode {
    /** Full power, rung fixed, no controller. */
    BENCH,
    /** The controller aims the whole fight at the target time. */
    PLAYER,
    /** {@link #PLAYER} against players and {@link #BENCH} against entities. */
    AUTO;

    /** Delivery default (errata E22-1). */
    public static final DifficultyMode DEFAULT = AUTO;

    /**
     * Parses a config token.
     *
     * @param text {@code auto}, {@code bench} or {@code player}
     * @return the mode
     * @throws IllegalArgumentException if the token is not one of those
     */
    public static DifficultyMode parse(String text) {
        return switch (text) {
            case "auto" -> AUTO;
            case "bench" -> BENCH;
            case "player" -> PLAYER;
            default -> throw new IllegalArgumentException("mode");
        };
    }
}
