package akyrion.thepredict.domain;

/** How request bodies are kept in the local JSONL (M21.1). */
public enum RecordBodies {
    /** No body line. */
    NONE,
    /** A {@code body_ref} line with the SHA-256 only. */
    HASH,
    /** The first occurrence of a hash also writes the body once. */
    DEDUP,
    /** Every sent body is written. */
    ALL;

    /**
     * Parses a configuration word.
     *
     * @param word the configured word
     * @return the policy
     * @throws IllegalArgumentException if the word is not a policy
     */
    public static RecordBodies parse(String word) {
        return switch (word) {
            case "none" -> NONE;
            case "hash" -> HASH;
            case "dedup" -> DEDUP;
            case "all" -> ALL;
            default -> throw new IllegalArgumentException(
                    "records.bodies must be none, hash, dedup or all, was " + word);
        };
    }
}
