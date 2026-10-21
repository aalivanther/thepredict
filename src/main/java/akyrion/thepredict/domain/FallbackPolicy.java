package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure rule that chooses the control level of a session. Stateless, reads no clock, safe on any
 * thread. Every drop to {@code LOCAL} or {@code BASE} carries a {@link FallbackReason} (rule A7).
 *
 * <p>Order: an invalid session goes to {@code BASE}; a valid {@code JEV} or {@code ALTERNATIVE}
 * intent keeps its level; a service that is not {@code READY}, an exhausted budget or a streak of
 * {@value #LOW_CONFIDENCE_STREAK_FOR_LOCAL} low-confidence answers (or the configured streak) goes to {@code LOCAL} when a
 * local tactic is available and to {@code BASE} otherwise; a valid {@code LOCAL} intent keeps
 * {@code LOCAL} while a local tactic is available, until an answer replaces it; otherwise the
 * session goes to {@code BASE} with {@code EXPIRED}: the intent expired, lost its local tactic or
 * never existed, and nothing replaces it.
 */
public final class FallbackPolicy {
    /** Consecutive low-confidence answers that move a session to the local planner. */
    public static final int LOW_CONFIDENCE_STREAK_FOR_LOCAL = 3;


    private FallbackPolicy() {
    }

    /**
     * Chooses the control level.
     *
     * @param service state of the decision service
     * @param budgetAvailable whether the request budget allows a new request
     * @param lowConfidenceStreak consecutive answers rejected for low confidence, {@code >= 0}
     * @param sessionValid whether the session is still valid
     * @param validIntentLevel level of the current unexpired intent, {@code BASE} when there is none
     * @param localTacticAvailable whether the local planner has a tactic
     * @return the level and, for a drop, its reason
     */
    public static FallbackDecision level(ServiceState service, boolean budgetAvailable, int lowConfidenceStreak,
                                         boolean sessionValid, ControlLevel validIntentLevel,
                                         boolean localTacticAvailable) {
        return level(service, budgetAvailable, lowConfidenceStreak, LOW_CONFIDENCE_STREAK_FOR_LOCAL, sessionValid,
                validIntentLevel, localTacticAvailable);
    }

    /**
     * Chooses the control level with the configured streak ({@code decision.lowConfidenceStreakForLocal}) instead
     * of {@value #LOW_CONFIDENCE_STREAK_FOR_LOCAL}.
     *
     * @param service state of the decision service
     * @param budgetAvailable whether the request budget allows a new request
     * @param lowConfidenceStreak consecutive answers rejected for low confidence, {@code >= 0}
     * @param streakForLocal streak that moves the session to the local planner, {@code >= 1}
     * @param sessionValid whether the session is still valid
     * @param validIntentLevel level of the current unexpired intent, {@code BASE} when there is none
     * @param localTacticAvailable whether the local planner has a tactic
     * @return the level and, for a drop, its reason
     * @throws IllegalArgumentException if the streak is negative or the threshold is below 1
     */
    public static FallbackDecision level(ServiceState service, boolean budgetAvailable, int lowConfidenceStreak,
                                         int streakForLocal, boolean sessionValid, ControlLevel validIntentLevel,
                                         boolean localTacticAvailable) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(validIntentLevel, "validIntentLevel");
        if (lowConfidenceStreak < 0) {
            throw new IllegalArgumentException("lowConfidenceStreak must be >= 0, was " + lowConfidenceStreak);
        }
        if (streakForLocal < 1) {
            throw new IllegalArgumentException("streakForLocal must be >= 1, was " + streakForLocal);
        }
        if (!sessionValid) {
            return drop(ControlLevel.BASE, FallbackReason.SESSION_INVALID);
        }
        if (validIntentLevel == ControlLevel.JEV || validIntentLevel == ControlLevel.ALTERNATIVE) {
            return new FallbackDecision(validIntentLevel, Optional.empty());
        }
        var degraded = degradation(service, budgetAvailable, lowConfidenceStreak >= streakForLocal);
        if (degraded.isPresent()) {
            return drop(localTacticAvailable ? ControlLevel.LOCAL : ControlLevel.BASE, degraded.orElseThrow());
        }
        if (validIntentLevel == ControlLevel.LOCAL && localTacticAvailable) {
            return new FallbackDecision(ControlLevel.LOCAL, Optional.empty());
        }
        return drop(ControlLevel.BASE, FallbackReason.EXPIRED);
    }

    private static Optional<FallbackReason> degradation(ServiceState service, boolean budgetAvailable,
                                                        boolean lowConfidenceRepeated) {
        return switch (service) {
            case READY -> {
                if (!budgetAvailable) {
                    yield Optional.of(FallbackReason.NO_BUDGET);
                }
                yield lowConfidenceRepeated ? Optional.of(FallbackReason.LOW_CONFIDENCE) : Optional.empty();
            }
            case PAUSED, CHECKING -> Optional.of(FallbackReason.SERVICE_PAUSED);
            case UNCONFIGURED, INVALID_CONFIGURATION, INVALID_CREDENTIALS, INVALID_MODEL ->
                    Optional.of(FallbackReason.SERVICE_BLOCKED);
        };
    }

    private static FallbackDecision drop(ControlLevel level, FallbackReason reason) {
        return new FallbackDecision(level, Optional.of(reason));
    }
}
