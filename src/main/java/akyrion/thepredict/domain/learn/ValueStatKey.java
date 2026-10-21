package akyrion.thepredict.domain.learn;

import java.util.Objects;

/**
 * One row of {@code m28_value_stats} (M28.1). {@code week} is UTC epoch days divided by 7, see {@link #week(long)}.
 *
 * @param profile profile id
 * @param tactic tactic id
 * @param context context text
 * @param source training source token
 * @param spec reward spec, {@code r1}
 * @param week week index
 */
public record ValueStatKey(String profile, String tactic, String context, String source, String spec, int week) {
    /** Milliseconds in one week. */
    public static final long WEEK_MS = 7L * 86_400_000L;

    /**
     * Validates the key.
     */
    public ValueStatKey {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(spec, "spec");
    }

    /**
     * Week index of a wall time: UTC epoch days divided by 7, rounded down. Saturates instead of overflowing.
     *
     * @param epochMs wall time supplied by the adapter
     * @return the week index
     */
    public static int week(long epochMs) {
        var week = Math.floorDiv(epochMs, WEEK_MS);
        if (week > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return week < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) week;
    }
}
