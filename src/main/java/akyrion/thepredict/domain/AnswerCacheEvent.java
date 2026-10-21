package akyrion.thepredict.domain;

/**
 * What the engine tells a listener about one cache lookup (M21.7).
 *
 * @param mode mode that produced the event
 * @param hit whether the lookup hit
 * @param ageTicks ticks since the cached capture
 * @param agreeBest whether the cached best tactic matched the live one
 * @param confidenceDelta live confidence minus cached confidence
 * @param tokensSaved input tokens the hit did not spend
 */
public record AnswerCacheEvent(AnswerCacheMode mode, boolean hit, long ageTicks, boolean agreeBest,
                               double confidenceDelta, long tokensSaved) {
    /**
     * Validates the event.
     *
     * @throws IllegalArgumentException if a count is negative or the delta is not finite
     */
    public AnswerCacheEvent {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (ageTicks < 0 || tokensSaved < 0 || !Double.isFinite(confidenceDelta)) {
            throw new IllegalArgumentException("invalid answer cache event");
        }
    }
}
