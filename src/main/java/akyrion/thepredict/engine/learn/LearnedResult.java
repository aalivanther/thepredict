package akyrion.thepredict.engine.learn;

import java.util.Locale;

/**
 * Result of the first damaging cast of a learned exploit (M28.7, EFO-24). {@link #NEUTRAL} is the result before that
 * cast, and the one a miss keeps. The token is the text of {@code extra.result} in the records.
 */
public enum LearnedResult {
    /** No damaging cast yet, or one that settled nothing. */
    NEUTRAL,
    /** The cast went out. */
    LANDED,
    /** A shield took the hit. */
    BLOCKED,
    /** MythicMobs refused the cast. */
    AVOIDED;

    /**
     * The record token.
     *
     * @return {@code neutral}, {@code landed}, {@code blocked} or {@code avoided}
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }
}
