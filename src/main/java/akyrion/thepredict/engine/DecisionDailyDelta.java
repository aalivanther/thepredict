package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * One day's decision counters for the {@code decision_daily} columns (M21.6, errata E21-4). The fields are exactly
 * those columns except the JSON {@code breakdown}, which stays empty in this release.
 *
 * @param day UTC day {@code yyyy-MM-dd}
 * @param model effective model
 * @param profile profile id, or {@code -} when the row is not per profile
 * @param questionDetail question detail word
 * @param requests requests planned
 * @param answered live answers
 * @param failed failures
 * @param inputTokens input tokens of live answers
 * @param answeredCache answers served from the cache
 * @param inputTokensSaved tokens those cache answers did not spend
 * @param adopted answers adopted
 * @param discarded answers discarded
 */
public record DecisionDailyDelta(String day, String model, String profile, String questionDetail, long requests,
                                 long answered, long failed, long inputTokens, long answeredCache,
                                 long inputTokensSaved, long adopted, long discarded) {
    /**
     * Validates the row.
     *
     * @throws NullPointerException if a text is {@code null}
     * @throws IllegalArgumentException if a count is negative
     */
    public DecisionDailyDelta {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(questionDetail, "questionDetail");
        if (requests < 0 || answered < 0 || failed < 0 || inputTokens < 0 || answeredCache < 0
                || inputTokensSaved < 0 || adopted < 0 || discarded < 0) {
            throw new IllegalArgumentException("decision daily counts must be >= 0");
        }
    }
}
