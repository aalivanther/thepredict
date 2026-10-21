package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Control level chosen by {@link FallbackPolicy} and why. Immutable.
 *
 * @param level control level
 * @param reason cause of a drop to {@code LOCAL} or {@code BASE}; empty when nothing degraded
 */
public record FallbackDecision(ControlLevel level, Optional<FallbackReason> reason) {
    /**
     * Validates the decision.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public FallbackDecision {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reason, "reason");
    }
}
