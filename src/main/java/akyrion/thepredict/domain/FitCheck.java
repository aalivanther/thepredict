package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * One optional {@code noul} fit check of M16.5: is this option of the {@code NOW} question of a mob a sound tactic
 * right now? It shares the key sequence {@code q0}, {@code q1}… of its request (rule I3). Immutable.
 *
 * @param key opaque key of the check, unique within the request
 * @param mob mob asked about
 * @param tactic one option sent in the {@code NOW} question of that mob
 */
public record FitCheck(QuestionKey key, MobKey mob, TacticId tactic) {
    /**
     * Validates the check.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public FitCheck {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(tactic, "tactic");
    }
}
