package akyrion.thepredict.domain;

import java.util.UUID;

/**
 * One pattern transition for the decision log (M26.9).
 *
 * @param participant the enemy
 * @param pattern the pattern
 * @param cause why it was recorded
 * @param state the state after the change
 */
public record PatternChange(UUID participant, EnemyPattern pattern, PatternCause cause, PatternState state) {
    /**
     * Checks the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PatternChange {
        if (participant == null || pattern == null || cause == null || state == null) {
            throw new NullPointerException("pattern change");
        }
    }
}
