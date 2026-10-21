package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.Steering;

import java.util.Optional;
import java.util.UUID;

/**
 * Q4 asked before a micro path (M24.5). {@link #KEEP} accepts the point. An empty answer does not call {@code moveTo}.
 */
@FunctionalInterface
public interface DestinationGuard {
    /** Accepts every point. */
    DestinationGuard KEEP = (entityId, behaviour, point, blockX, blockZ) -> Optional.of(point);

    /**
     * The destination to path to.
     *
     * @param entityId the mob
     * @param behaviour the micro
     * @param point the point the geometry chose
     * @param blockX mob column
     * @param blockZ mob column
     * @return the point, or empty when the zone refuses it
     */
    Optional<Steering> ask(UUID entityId, MicroBehaviour behaviour, Steering point, int blockX, int blockZ);
}
