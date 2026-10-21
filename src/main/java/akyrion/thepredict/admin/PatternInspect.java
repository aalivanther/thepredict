package akyrion.thepredict.admin;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternState;

/**
 * Text of {@code inspect … patterns} (M26.11). It names states and counts, not a recommendation.
 */
public final class PatternInspect {
    private PatternInspect() {
    }

    /**
     * One block.
     *
     * @param tokens tokens of the last 40 ticks
     * @param forecast forecast name, or {@code none}
     * @param accuracyPermille running accuracy in thousandths
     * @param pattern the pattern
     * @param state its state
     * @param confidenceMillis confidence
     * @param trials trials
     * @param streak success streak
     * @param resting whether it is resting
     * @param quotaUsed motivated casts in the window
     * @param quotaCap casts allowed
     * @param facts filtered fact names
     * @return the lines
     */
    public static String format(String tokens, String forecast, int accuracyPermille, EnemyPattern pattern,
                                PatternState state, int confidenceMillis, int trials, int streak, boolean resting,
                                int quotaUsed, int quotaCap, String facts) {
        return "tokens " + tokens + "\nforecast " + forecast + " accuracy " + accuracyPermille
                + "\n" + pattern.name().toLowerCase(java.util.Locale.ROOT) + " " + state.name().toLowerCase(java.util.Locale.ROOT)
                + " confidence " + confidenceMillis + " trials " + trials + " streak " + streak
                + " rest " + resting + "\nquota " + quotaUsed + "/" + quotaCap + "\nfacts " + facts;
    }
}
