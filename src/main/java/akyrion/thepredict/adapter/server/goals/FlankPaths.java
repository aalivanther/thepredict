package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.context.ShieldGeometry;
import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;

/**
 * Path choice and re-planning rule of {@link FlankGoal}. Package-private; {@link #shortestReachable} runs on the
 * server thread, {@link #targetMovedOrTurned} is pure.
 */
final class FlankPaths {
    /** Target displacement that justifies a new plan, in blocks. */
    static final double REPLAN_DISTANCE_BLOCKS = 1.0;

    /** Target rotation that justifies a new plan, in degrees. */
    static final double REPLAN_TURN_DEGREES = 30.0;

    private FlankPaths() {
    }

    /**
     * Asks the pathfinder for a path to the point on each side of the target and keeps the one that reaches
     * its final point with fewer points. Exactly two {@code findPath} calls.
     *
     * @param pathfinder the mob's pathfinder
     * @param target target position and yaw
     * @param turnDegrees turn from the target's facing to each side
     * @param reachBlocks distance from the target to each point
     * @return the chosen path, or empty when neither side is reachable
     */
    static Optional<Pathfinder.PathResult> shortestReachable(Pathfinder pathfinder, Location target, double turnDegrees,
                                                             double reachBlocks) {
        return shortestReachable(pathfinder, target, turnDegrees, reachBlocks, FlankSafety.ALLOW, null);
    }

    /**
     * The shorter reachable side whose final point and path points the safety accepts.
     *
     * @param pathfinder the mob's pathfinder
     * @param target target position and yaw
     * @param turnDegrees turn from the target's facing to each side
     * @param reachBlocks distance from the target to each point
     * @param safety Q4 and the edge filter; {@link FlankSafety#ALLOW} keeps today's choice
     * @param entityId the mob, passed to the safety
     * @return the chosen path, or empty when neither side is safe and reachable
     */
    static Optional<Pathfinder.PathResult> shortestReachable(Pathfinder pathfinder, Location target, double turnDegrees,
                                                             double reachBlocks, FlankSafety safety, UUID entityId) {
        Pathfinder.PathResult left = null;
        Pathfinder.PathResult right = null;
        var index = 0;
        for (var side : new double[] {turnDegrees, -turnDegrees}) {
            var destination = target.clone().add(ShieldGeometry.offset(target.getYaw(), side, reachBlocks));
            var path = pathfinder.findPath(destination);
            if (path != null && path.canReachFinalPoint() && safe(safety, entityId, destination, path)) {
                if (index == 0) {
                    left = path;
                } else {
                    right = path;
                }
            }
            index++;
        }
        var chosen = prefer(left == null ? 0 : left.getPoints().size(), left != null,
                right == null ? 0 : right.getPoints().size(), right != null);
        if (chosen < 0) {
            return Optional.empty();
        }
        return Optional.of(chosen == 0 ? left : right);
    }

    /**
     * Which safe side is shorter. An unsafe side is not a candidate.
     *
     * @param leftPoints points on the positive turn, ignored when that side is unsafe
     * @param leftSafe whether the positive turn passed Q4
     * @param rightPoints points on the negative turn
     * @param rightSafe whether the negative turn passed Q4
     * @return {@code 0} for the positive turn, {@code 1} for the other, or {@code -1} when neither is safe
     */
    static int prefer(int leftPoints, boolean leftSafe, int rightPoints, boolean rightSafe) {
        if (!leftSafe && !rightSafe) {
            return -1;
        }
        if (!leftSafe) {
            return 1;
        }
        if (!rightSafe) {
            return 0;
        }
        return leftPoints <= rightPoints ? 0 : 1;
    }

    private static boolean safe(FlankSafety safety, UUID entityId, Location destination, Pathfinder.PathResult path) {
        if (!safety.allows(entityId, destination)) {
            return false;
        }
        var points = path.getPoints();
        for (var point : points) {
            if (!safety.allows(entityId, point)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether the target moved more than {@link #REPLAN_DISTANCE_BLOCKS} or turned more than
     * {@link #REPLAN_TURN_DEGREES} since the last plan.
     *
     * @param previous target position and yaw at the last plan
     * @param current target position and yaw now
     * @return {@code true} when a new plan is justified
     */
    static boolean targetMovedOrTurned(Location previous, Location current) {
        var dx = current.getX() - previous.getX();
        var dy = current.getY() - previous.getY();
        var dz = current.getZ() - previous.getZ();
        var turn = Math.abs(wrapDegrees(current.getYaw() - previous.getYaw()));
        return dx * dx + dy * dy + dz * dz > REPLAN_DISTANCE_BLOCKS * REPLAN_DISTANCE_BLOCKS || turn > REPLAN_TURN_DEGREES;
    }

    /** Wraps an angle difference into {@code [-180, 180)} degrees. */
    static double wrapDegrees(double degrees) {
        var wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }
}
