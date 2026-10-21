package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BetaMath;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PatternTracker;

/**
 * Text of {@code inspect patterns} from the model the step already holds (M26.11). Confidence is read here, not on
 * the tick. A missing model is not invented by the caller.
 */
public final class PatternReport {
    private PatternReport() {
    }

    static PatternSnapshot line(MobSession session, EnemyModel learned) {
        var tracker = busiest(learned);
        var pattern = tracker == null ? EnemyPattern.SPAMS_CLICKS : tracker.pattern();
        var state = tracker == null ? PatternState.INACTIVE : tracker.state();
        var forecast = learned.forecast() < 0 ? "none"
                : EnemyAction.values()[learned.forecast()].name().toLowerCase(java.util.Locale.ROOT);
        var accuracy = (int) Math.round(learned.accuracy() * 1000.0);
        var confidence = tracker == null ? 0 : (int) Math.round(1000.0 * BetaMath.lowerQuantile(0.05,
                tracker.evidence().alpha(), tracker.evidence().beta()));
        var trials = tracker == null ? 0 : tracker.evidence().trials();
        var streak = tracker == null ? 0 : tracker.streak();
        var resting = state == PatternState.RESTING || session.patternResting(session.patternSeenTick());
        var cap = session.patternShare() <= 0.0 ? 0 : (int) Math.ceil(session.patternShare() * 16.0);
        return new PatternSnapshot(learned.tokens(), forecast, accuracy, pattern, state, confidence, trials, streak,
                resting, session.patternMotivated(), cap, facts(session));
    }

    private static PatternTracker busiest(EnemyModel learned) {
        PatternTracker best = null;
        for (var pattern : EnemyPattern.values()) {
            var tracker = learned.tracker(pattern);
            if (tracker == null) {
                continue;
            }
            if (best == null || tracker.evidence().trials() > best.evidence().trials()
                    || tracker.state() == PatternState.ACTIVE && best.state() != PatternState.ACTIVE) {
                best = tracker;
            }
        }
        return best;
    }

    private static String facts(MobSession session) {
        var admitted = session.admittedPatterns();
        if (admitted.isEmpty()) {
            return session.patternMask() == 0 ? "none" : Integer.toString(session.patternMask());
        }
        var text = new StringBuilder();
        for (var pattern : admitted) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(pattern.name().toLowerCase(java.util.Locale.ROOT));
        }
        return text.toString();
    }

    /**
     * One inspect line read from the model.
     *
     * @param tokens recent actions, or {@code none}
     * @param forecast forecast name, or {@code none}
     * @param accuracyPermille forecast accuracy in thousandths
     * @param pattern the busiest tracker
     * @param state state of that tracker
     * @param confidenceMillis lower quantile of the evidence, in thousandths
     * @param trials trials of that tracker
     * @param streak success streak
     * @param resting whether a rest is open
     * @param quotaUsed motivated casts in the window
     * @param quotaCap casts the share allows in a window of 16
     * @param facts admitted fact names, or {@code none}
     */
    public record PatternSnapshot(String tokens, String forecast, int accuracyPermille, EnemyPattern pattern,
                           PatternState state, int confidenceMillis, int trials, int streak, boolean resting,
                           int quotaUsed, int quotaCap, String facts) {
    }
}
