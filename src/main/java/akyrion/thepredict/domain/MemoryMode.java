package akyrion.thepredict.domain;

/**
 * How much of a player's memory may affect a fight (M27). {@link #RECORD} is the shipped mode and matches {@link #OFF}
 * on every body sent to Jev.
 */
public enum MemoryMode {
    /** Nothing is loaded and nothing is written. */
    OFF(0),
    /** Digests are stored. The fight is unchanged. */
    RECORD(1),
    /** A bounded seed and a rung prior may apply. */
    SEED(2),
    /** Seed, rung prior and one fixed history phrase. */
    FULL(3);

    /** The mode of a fresh installation. */
    public static final MemoryMode DEFAULT = RECORD;

    private final int rank;

    MemoryMode(int rank) {
        this.rank = rank;
    }

    /**
     * The lower of the two modes. A profile may only lower the global mode.
     *
     * @param other the other mode
     * @return the minimum
     */
    public MemoryMode min(MemoryMode other) {
        return rank <= other.rank ? this : other;
    }

    /**
     * Order used by {@link #min}.
     *
     * @return {@code 0} for off through {@code 3} for full
     */
    public int rank() {
        return rank;
    }

    /**
     * Parses a configuration word.
     *
     * @param word the word
     * @return the mode
     */
    public static MemoryMode parse(String word) {
        return switch (word) {
            case "off" -> OFF;
            case "record" -> RECORD;
            case "seed" -> SEED;
            case "full" -> FULL;
            default -> throw new IllegalArgumentException("memory mode must be off, record, seed or full, was " + word);
        };
    }
}
