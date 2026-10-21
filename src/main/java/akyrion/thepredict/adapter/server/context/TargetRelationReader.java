package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.TargetRelation;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Describes the tactical situation between a mob and its target with meaning instead of coordinates (M07.4, M17.4).
 * The rules are pure static methods; {@link #read} gathers their inputs on the server thread. The trend comes from
 * {@link TargetTrend} and the reach of the target from {@link TargetReach}, each compared the way its source measures
 * it (DT-36, DT-37).
 */
final class TargetRelationReader {
    private final EntityReads reads;

    TargetRelationReader(EntityReads reads) {
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    /**
     * Builds the relation of a member mob to its target.
     *
     * @return the relation with the bands of the profile
     */
    TargetRelation read(MobKey key, LivingEntity mob, LivingEntity target, ParticipantAlias alias,
                        double distanceBlocks, ParticipantReading reading, MovementSettings movement,
                        Perception perception, CaptureContext context, boolean grouped, long kinematics) {
        var mobAt = mob.getLocation();
        var targetAt = target.getLocation();
        var trend = TargetTrend.of(target, mobAt, targetAt, kinematics);
        var previous = context.previousTargetDistanceBlocks().get(key);
        if (previous != null) {
            trend = TargetTrend.correctedByDistance(trend, distanceBlocks, previous);
        }
        var visible = Perception.visible(perception.lineOfSight(mob, target), reading.invisible());
        var reachable = reachable(reads.currentPath(mob), context.allowPathfinding().contains(key),
                () -> reads.findPathReaches(mob, target), context.lastUnreachable().contains(key));
        var shieldUp = TargetCueReader.blocking(reading) || TargetCueReader.raisingShield(reading);
        var outsideAngle = shieldUp && visible && ShieldGeometry.isOutsideBlockingAngle(targetAt, mobAt,
                reads.raisedShieldAngleDegrees(target).orElse(ShieldGeometry.DEFAULT_BLOCKING_ANGLE_DEGREES));
        var outsideReach = reads.targetReach(target).outside(mob, target, distanceBlocks);
        return new TargetRelation(alias, Bands.distance(distanceBlocks, movement), trend, reachable, visible,
                outsideReach, flankable(shieldUp, visible, outsideAngle), grouped);
    }

    /**
     * Grouped (M15.5): another perceived participant stands within {@link TargetRelation#GROUP_RADIUS_BLOCKS} of the
     * target, measured between their positions (for two players, whose boxes share one size, the distance between
     * their centres). At most one squared distance per participant (at most 16), without allocating.
     *
     * @param targetId the target
     * @param positions positions of the perceived participants of the capture, the target included
     * @return whether the target stands close to another participant
     */
    static boolean grouped(UUID targetId, Map<UUID, Location> positions) {
        var at = positions.get(targetId);
        if (at == null) {
            return false;
        }
        var limit = TargetRelation.GROUP_RADIUS_BLOCKS * TargetRelation.GROUP_RADIUS_BLOCKS;
        for (var entry : positions.entrySet()) {
            if (!entry.getKey().equals(targetId)) {
                var other = entry.getValue();
                var x = other.getX() - at.getX();
                var y = other.getY() - at.getY();
                var z = other.getZ() - at.getZ();
                if (x * x + y * y + z * z <= limit) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reachability: the path in progress when there is one; otherwise a new path only with the engine's
     * permission; otherwise the last known value, {@code true} when none is known.
     *
     * @param current path the navigator already follows
     * @param allowed whether the engine allowed a path computation for this mob in this capture
     * @param computePath computes a new path and tells whether it reaches the target
     * @param lastUnreachable whether the engine last saw the target unreachable
     * @return whether the target counts as reachable
     */
    static boolean reachable(EntityReads.CurrentPath current, boolean allowed, BooleanSupplier computePath,
                             boolean lastUnreachable) {
        return switch (current) {
            case REACHES -> true;
            case FALLS_SHORT -> false;
            case NONE -> allowed ? computePath.getAsBoolean() : !lastUnreachable;
        };
    }

    /**
     * Flankable: the target is raising or blocking with a shield, the mob sees it, and the mob is still inside the
     * blocking angle, so moving around it would escape the shield.
     *
     * @param shieldUp {@code BLOCKING} or {@code RAISING_SHIELD}
     * @param visible whether the mob sees the target
     * @param outsideBlockingAngle whether the mob already stands outside the blocking angle
     * @return whether flanking is worth it
     */
    static boolean flankable(boolean shieldUp, boolean visible, boolean outsideBlockingAngle) {
        return shieldUp && visible && !outsideBlockingAngle;
    }
}
