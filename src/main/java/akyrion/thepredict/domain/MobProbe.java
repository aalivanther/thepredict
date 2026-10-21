package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cheap reading of one mob between captures, used for contingencies, target changes and encounter formation.
 * Immutable.
 *
 * @param mob mob session
 * @param worldId world where the mob is now, so {@code engine} can place it in an encounter of that world (M09)
 * @param health health band of the mob
 * @param targetId UUID of the current target, if any
 * @param targetDistance distance band to the target; present only with a target
 */
public record MobProbe(MobKey mob, UUID worldId, HealthBand health, Optional<UUID> targetId,
                       Optional<DistanceBand> targetDistance) {
    /**
     * Validates the probe.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a distance is present without a target
     */
    public MobProbe {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetDistance, "targetDistance");
        if (targetDistance.isPresent() && targetId.isEmpty()) {
            throw new IllegalArgumentException("targetDistance requires a targetId");
        }
    }
}
