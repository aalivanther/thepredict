package akyrion.thepredict.adapter.server.goals;

import java.util.Objects;

/**
 * Place of one Paper goal in a generated goal set. Immutable.
 *
 * @param kind which Paper goal
 * @param priority goal selector priority, {@code >= 0}; lower runs first
 */
public record PaperGoalSlot(PaperGoalKind kind, int priority) {
    /**
     * Validates the slot.
     *
     * @throws NullPointerException if {@code kind} is {@code null}
     * @throws IllegalArgumentException if {@code priority < 0}
     */
    public PaperGoalSlot {
        Objects.requireNonNull(kind, "kind");
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be >= 0, was " + priority);
        }
    }
}
