package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * One day's cache counters for the {@code answer_cache_daily} columns (M21.6, errata E21-4).
 *
 * @param day UTC day {@code yyyy-MM-dd}
 * @param model model consulted
 * @param mode {@code off}, {@code shadow} or {@code on}
 * @param lookups lookups
 * @param hits hits
 * @param compared shadow comparisons
 * @param agreeBest comparisons whose best tactic matched
 * @param confidenceDeltaP95 95th percentile of the absolute confidence delta, or {@code NaN} when unknown
 */
public record AnswerCacheDelta(String day, String model, String mode, long lookups, long hits, long compared,
                               long agreeBest, double confidenceDeltaP95) {
    /**
     * Validates the row.
     *
     * @throws NullPointerException if a text is {@code null}
     * @throws IllegalArgumentException if a count is negative
     */
    public AnswerCacheDelta {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(mode, "mode");
        if (lookups < 0 || hits < 0 || compared < 0 || agreeBest < 0) {
            throw new IllegalArgumentException("answer cache daily counts must be >= 0");
        }
    }
}
