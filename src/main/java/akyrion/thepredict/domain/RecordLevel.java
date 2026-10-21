package akyrion.thepredict.domain;

/** How much of a fight is written to the local JSONL (M21.1). */
public enum RecordLevel {
    /** Nothing is built or written. */
    OFF,
    /** Decisions, results, intents, outcomes and encounter closes. */
    DECISIONS,
    /** {@link #DECISIONS} plus casts, threat, reactions and micro. */
    FULL;

    /**
     * Parses a configuration word.
     *
     * @param word the configured word
     * @return the level
     * @throws IllegalArgumentException if the word is not a level
     */
    public static RecordLevel parse(String word) {
        return switch (word) {
            case "off" -> OFF;
            case "decisions" -> DECISIONS;
            case "full" -> FULL;
            default -> throw new IllegalArgumentException("records.level must be off, decisions or full, was " + word);
        };
    }
}
