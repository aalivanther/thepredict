package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of one incarnation of a bound mob (rule I1).
 *
 * <p>The entity UUID matches the MythicMobs {@code ActiveMob} UUID. The generation grows on spawn,
 * chunk load, world change, reload and Predictor change, so a decision for generation 3 never
 * applies to generation 4. The integer entity ID is never used because the server reuses it.
 * Immutable and safe to share between threads.
 *
 * @param entityId UUID of the entity; never {@code null}
 * @param generation incarnation counter, {@code >= 0}
 */
public record MobKey(UUID entityId, int generation) {
    /**
     * Validates the identity.
     *
     * @throws NullPointerException if {@code entityId} is {@code null}
     * @throws IllegalArgumentException if {@code generation} is negative
     */
    public MobKey {
        Objects.requireNonNull(entityId, "entityId");
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be >= 0, was " + generation);
        }
    }
}
