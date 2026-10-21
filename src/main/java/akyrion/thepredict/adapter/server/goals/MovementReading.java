package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.MovementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Mob;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Reads the movement of a mob for {@link GoalOwnership#progress} (M05.5) from state the navigator already
 * has: no new path is computed. Package-private; server thread only.
 */
final class MovementReading {
    /** Progress of a mob that is not available: nothing runs and there is no distance. */
    static final MovementProgress NONE = new MovementProgress(false, false, false, OptionalDouble.empty());

    private MovementReading() {
    }

    /**
     * Builds the progress of a mob.
     *
     * @param mob entity to read
     * @param movementGoalRunning whether a goal other than {@code hold} runs with {@code MOVE}
     * @return goal, path and target distance state
     */
    static MovementProgress of(Mob mob, boolean movementGoalRunning) {
        var pathfinder = mob.getPathfinder();
        var hasPath = pathfinder.hasPath();
        var path = pathfinder.getCurrentPath();
        var reaches = hasPath && path != null && path.canReachFinalPoint();
        return new MovementProgress(movementGoalRunning, hasPath, reaches, targetDistanceBlocks(mob));
    }

    /**
     * Distance from the mob to its target when both are in the same world.
     *
     * @param mob entity to read
     * @return distance in blocks, or empty without a target in the same world
     */
    static OptionalDouble targetDistanceBlocks(Mob mob) {
        var target = mob.getTarget();
        if (target == null) {
            return OptionalDouble.empty();
        }
        var from = mob.getLocation();
        var to = target.getLocation();
        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            return OptionalDouble.empty();
        }
        var dx = to.getX() - from.getX();
        var dy = to.getY() - from.getY();
        var dz = to.getZ() - from.getZ();
        return OptionalDouble.of(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * Follow range of the mob from its {@code FOLLOW_RANGE} attribute, which MythicMobs sets from
     * {@code Options.FollowRange}. Server thread only; the attribute registry needs a running server.
     *
     * @param mob entity to read
     * @param fallbackBlocks value used when the attribute is missing, not positive or infinite
     * @return follow range in blocks
     */
    static double followRangeBlocks(Mob mob, double fallbackBlocks) {
        var attribute = mob.getAttribute(Attribute.FOLLOW_RANGE);
        var value = attribute == null ? 0.0 : attribute.getValue();
        return value > 0 && !Double.isInfinite(value) ? value : fallbackBlocks;
    }
}
