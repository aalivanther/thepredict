package akyrion.thepredict.adapter.server.context;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Horizontal geometry of vanilla shield blocking (plan §2 and §5, M05.3), shared by {@code FlankGoal}, the
 * context capture of M07 and the cast check of M10. Stateless.
 *
 * <p>Vanilla 26.2 blocks when {@code acos(dot(view, direction)) <= horizontalBlockingAngle}, where
 * {@code view} is the blocker's horizontal facing and {@code direction} the normalized horizontal vector from
 * the blocker to the attacker; with no horizontal offset the direction is zero and the angle is 90°
 * ({@code LivingEntity.applyItemBlocking} and {@code BlocksAttacks.DamageReduction.resolve}, javap). Minecraft
 * yaw gives the facing {@code x = -sin(yaw)}, {@code z = cos(yaw)}. The pure methods run on any thread;
 * {@link #raisedShieldAngleDegrees} reads an entity and runs on the server thread only.
 */
public final class ShieldGeometry {
    /** Blocking angle when the item declares no damage reduction, in degrees. */
    public static final double DEFAULT_BLOCKING_ANGLE_DEGREES = 90.0;

    /** Extra turn beyond the blocking angle used for flank points, in degrees. */
    public static final double FLANK_MARGIN_DEGREES = 20.0;

    /** Largest horizontal angle from the facing, in degrees: straight behind. */
    public static final double BEHIND_DEGREES = 180.0;

    private ShieldGeometry() {
    }

    /**
     * Horizontal offset from a target to a point turned from its facing.
     *
     * @param yawDegrees target yaw in Minecraft degrees
     * @param turnDegrees signed turn from the facing, positive to one side and negative to the other
     * @param distanceBlocks length of the offset
     * @return offset with {@code y = 0}
     */
    public static Vector offset(float yawDegrees, double turnDegrees, double distanceBlocks) {
        var radians = Math.toRadians(yawDegrees + turnDegrees);
        return new Vector(-Math.sin(radians) * distanceBlocks, 0.0, Math.cos(radians) * distanceBlocks);
    }

    /**
     * Angle between the target's facing and the horizontal direction from the target to the attacker.
     *
     * @param targetYawDegrees target yaw in Minecraft degrees
     * @param towardsAttackerX attacker x minus target x
     * @param towardsAttackerZ attacker z minus target z
     * @return angle in {@code [0, 180]} degrees; 90 when there is no horizontal offset, as in vanilla
     */
    public static double angleFromFacingDegrees(float targetYawDegrees, double towardsAttackerX, double towardsAttackerZ) {
        var length = Math.hypot(towardsAttackerX, towardsAttackerZ);
        if (length < 1.0E-5) {
            return 90.0;
        }
        var radians = Math.toRadians(targetYawDegrees);
        var dot = (-Math.sin(radians) * towardsAttackerX + Math.cos(radians) * towardsAttackerZ) / length;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    /**
     * Tells whether a hit from the attacker's position escapes a shield held by the target.
     *
     * @param target position and yaw of the blocking target
     * @param attacker position of the attacker
     * @param blockingAngleDegrees horizontal blocking angle of the shield
     * @return {@code true} if the angle from the facing is strictly greater than the blocking angle
     */
    public static boolean isOutsideBlockingAngle(Location target, Location attacker, double blockingAngleDegrees) {
        return angleFromFacingDegrees(target.getYaw(), attacker.getX() - target.getX(), attacker.getZ() - target.getZ())
                > blockingAngleDegrees;
    }

    /**
     * Turn from the facing used for the two flank points: the blocking angle plus
     * {@link #FLANK_MARGIN_DEGREES}, at most straight behind.
     *
     * @param blockingAngleDegrees horizontal blocking angle of the shield
     * @return turn in degrees
     */
    public static double flankTurnDegrees(double blockingAngleDegrees) {
        return Math.min(BEHIND_DEGREES, blockingAngleDegrees + FLANK_MARGIN_DEGREES);
    }

    /**
     * Blocking angle of an item from its damage reductions: the largest angle, or
     * {@link #DEFAULT_BLOCKING_ANGLE_DEGREES} when there is none.
     *
     * @param reductions damage reductions of the {@code BLOCKS_ATTACKS} component
     * @return angle in degrees
     */
    public static double blockingAngleDegrees(List<? extends DamageReduction> reductions) {
        if (reductions.isEmpty()) {
            return DEFAULT_BLOCKING_ANGLE_DEGREES;
        }
        var widest = 0.0;
        for (var reduction : reductions) {
            widest = Math.max(widest, reduction.horizontalBlockingAngle());
        }
        return widest;
    }

    /**
     * Reads the blocking angle of the shield any living target is raising or blocking with (M17.4): its active item
     * ({@code hasActiveItem()}, which replaced the obsolete {@code isHandRaised()}) must carry
     * {@code BLOCKS_ATTACKS}. Only its {@code damageReductions()} are read, for the angle. Server thread only.
     *
     * @param target target of the mob
     * @return the angle in degrees, or empty when the target is not raising such an item
     */
    public static OptionalDouble raisedShieldAngleDegrees(LivingEntity target) {
        if (!target.hasActiveItem()) {
            return OptionalDouble.empty();
        }
        var blocks = target.getActiveItem().getData(DataComponentTypes.BLOCKS_ATTACKS);
        return blocks == null ? OptionalDouble.empty() : OptionalDouble.of(blockingAngleDegrees(blocks.damageReductions()));
    }
}
