package akyrion.thepredict.domain;

/** Whether answers may be reused (M21.7). Delivered {@link #OFF}. */
public enum AnswerCacheMode {
    /** The cache is not consulted. */
    OFF,
    /** A lookup is recorded and the request is always sent. */
    SHADOW,
    /** A fresh exact hit can skip the send. */
    ON;

    /**
     * Parses a configuration word.
     *
     * @param word the configured word
     * @return the mode
     * @throws IllegalArgumentException if the word is not a mode
     */
    public static AnswerCacheMode parse(String word) {
        return switch (word) {
            case "off" -> OFF;
            case "shadow" -> SHADOW;
            case "on" -> ON;
            default -> throw new IllegalArgumentException("decision.answerCache must be off, shadow or on, was " + word);
        };
    }
}
