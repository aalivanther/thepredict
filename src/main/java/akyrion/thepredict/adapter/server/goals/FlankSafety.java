package akyrion.thepredict.adapter.server.goals;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Whether a flank point may be pathed to (M24.5). {@link #ALLOW} accepts every point.
 */
@FunctionalInterface
public interface FlankSafety {
    /** Accepts every point. */
    FlankSafety ALLOW = (entityId, point) -> true;

    /**
     * Whether the point may be used.
     *
     * @param entityId the mob
     * @param point a path point
     * @return {@code false} when the zone refuses the point
     */
    boolean allows(UUID entityId, Location point);
}
