package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * What one capture saw of a member mob, handed back to the engine through {@link CaptureContext#sightings()}, so
 * the engine keeps the world, the target and its distance while the adapter holds no state (rule A3). It never
 * enters {@link CombatFacts}: the target UUID and the raw distance stay in the engine (rule I6). Immutable.
 *
 * @param mob mob session
 * @param worldId world the mob stands in
 * @param targetId UUID of the current target, if any
 * @param targetDistanceBlocks distance to the target in blocks; present exactly when there is a target
 */
public record MobSighting(MobKey mob, UUID worldId, Optional<UUID> targetId, OptionalDouble targetDistanceBlocks) {
    /**
     * Validates the sighting.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the distance and the target do not come together, or the distance is
     *         negative or not finite
     */
    public MobSighting {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetDistanceBlocks, "targetDistanceBlocks");
        if (targetId.isPresent() != targetDistanceBlocks.isPresent()) {
            throw new IllegalArgumentException("targetDistanceBlocks must be present exactly with a targetId");
        }
        if (targetDistanceBlocks.isPresent()) {
            DomainChecks.requireNonNegative(targetDistanceBlocks.getAsDouble(), "targetDistanceBlocks");
        }
    }
}
