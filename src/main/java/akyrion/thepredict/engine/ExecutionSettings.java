package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * Settings of the tactical execution (M10), translated by the root from {@code config.DecisionSettings} and
 * {@code config.JevSettings} (rule D10). Immutable.
 *
 * @param maxDecisionAgeNanos oldest capture an answer may come from ({@code decision.maxDecisionAgeMillis}),
 *                            {@code > 0}
 * @param lowConfidenceStreakForLocal low-confidence answers in a row that move a session to the local planner
 *                                    ({@code decision.lowConfidenceStreakForLocal}), {@code >= 1}
 * @param configuredModel model of {@code jev.model}: a versioned ID or an alias
 * @param logDecisions whether each decision, discard and cast is logged ({@code decision.logDecisions}, rule C8)
 */
public record ExecutionSettings(long maxDecisionAgeNanos, int lowConfidenceStreakForLocal, String configuredModel,
                                boolean logDecisions) {
    /**
     * Validates the settings.
     *
     * @throws NullPointerException if the model is {@code null}
     * @throws IllegalArgumentException if a value is out of range or the model is blank
     */
    public ExecutionSettings {
        Objects.requireNonNull(configuredModel, "configuredModel");
        if (maxDecisionAgeNanos <= 0) {
            throw new IllegalArgumentException("maxDecisionAgeNanos must be > 0, was " + maxDecisionAgeNanos);
        }
        if (lowConfidenceStreakForLocal < 1) {
            throw new IllegalArgumentException("lowConfidenceStreakForLocal must be >= 1, was "
                    + lowConfidenceStreakForLocal);
        }
        if (configuredModel.isBlank()) {
            throw new IllegalArgumentException("configuredModel must not be blank");
        }
    }
}
