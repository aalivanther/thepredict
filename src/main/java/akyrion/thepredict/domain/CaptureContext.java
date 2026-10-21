package akyrion.thepredict.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * What the engine hands to one capture. Built and used on the server thread only, because
 * {@code aliasFor} assigns new aliases and {@code sightings} updates sessions in the engine (rule A3).
 *
 * @param aliasFor returns the stable alias of a participant UUID of a kind in the encounter; a UUID that already has
 *                 an alias keeps it (M17.1)
 * @param allowPathfinding mobs allowed to compute a path in this capture
 * @param previousTargetDistanceBlocks distance to the target at the previous capture, per mob
 * @param recentEvents copy of the encounter history, at most {@value CombatFacts#MAX_EVENTS} events
 * @param worldId world of the encounter; a member seen in another world is left out of the facts
 * @param lastUnreachable mobs whose last known target was unreachable, kept while no path answers again
 * @param sightings receives what the capture saw of each member it could read, including one in another world
 */
public record CaptureContext(BiFunction<UUID, ParticipantKind, ParticipantAlias> aliasFor,
                             Set<MobKey> allowPathfinding,
                             Map<MobKey, Double> previousTargetDistanceBlocks,
                             List<EncounterEvent> recentEvents, UUID worldId, Set<MobKey> lastUnreachable,
                             Consumer<MobSighting> sightings) {
    /**
     * Validates the context and copies its collections.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if a distance is negative or not finite, or there are too many events
     */
    public CaptureContext {
        Objects.requireNonNull(aliasFor, "aliasFor");
        allowPathfinding = Set.copyOf(Objects.requireNonNull(allowPathfinding, "allowPathfinding"));
        var distances = new HashMap<MobKey, Double>();
        Objects.requireNonNull(previousTargetDistanceBlocks, "previousTargetDistanceBlocks").forEach((mob, distance) ->
                distances.put(mob, DomainChecks.requireNonNegative(
                        Objects.requireNonNull(distance, "previousTargetDistanceBlocks"), "previousTargetDistanceBlocks")));
        previousTargetDistanceBlocks = Map.copyOf(distances);
        recentEvents = List.copyOf(Objects.requireNonNull(recentEvents, "recentEvents"));
        DomainChecks.requireMaximumSize(recentEvents, CombatFacts.MAX_EVENTS, "recentEvents");
        Objects.requireNonNull(worldId, "worldId");
        lastUnreachable = Set.copyOf(Objects.requireNonNull(lastUnreachable, "lastUnreachable"));
        Objects.requireNonNull(sightings, "sightings");
    }
}
