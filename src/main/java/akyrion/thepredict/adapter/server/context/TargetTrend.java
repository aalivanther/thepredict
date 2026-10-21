package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.EnemyKinematics;
import akyrion.thepredict.domain.Trend;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The trend of a mob's target (M07.4, M17.4, M26). No player velocity is ever read, and no raw input is read
 * (decisión 7, errata E26-1 aplicada). A player's trend comes from the kinematic word the engine stored; any other
 * entity has no such word and the server moves it, so its trend comes from its velocity (DT-36). An invalid word is
 * {@code STEADY}. The distance between captures corrects both. The rules are pure static methods; {@link #of} reads
 * the target on the server thread. The trend is computed in every pattern mode, including off.
 */
final class TargetTrend {
    /** Change of distance between captures, in blocks, beyond which it overrides the kinematic reading. */
    static final double DISTANCE_CHANGE_BLOCKS = 1.0;
    /** Horizontal speed, in blocks per tick, above which an entity counts as moving (M17.4). */
    static final double MOVING_BLOCKS_PER_TICK = 0.05;

    private TargetTrend() {
    }

    /**
     * The trend of a target. A player uses the kinematic word; any other entity uses its velocity.
     *
     * @param target the target
     * @param mobAt position of the mob
     * @param targetAt position of the target
     * @param kinematics opaque word from the engine; {@code 0} when the engine has no sample yet
     * @return the trend before the correction by distance
     */
    static Trend of(LivingEntity target, Location mobAt, Location targetAt, long kinematics) {
        if (target instanceof Player) {
            return trendFromKinematics(kinematics);
        }
        var velocity = target.getVelocity();
        return trendFromVelocity(mobAt.getX() - targetAt.getX(), mobAt.getZ() - targetAt.getZ(), velocity.getX(),
                velocity.getZ());
    }

    /**
     * The trend when the engine has not stored a kinematic word yet. A player is {@code STEADY}.
     *
     * @param target the target
     * @param mobAt position of the mob
     * @param targetAt position of the target
     * @return the trend before the correction by distance
     */
    static Trend of(LivingEntity target, Location mobAt, Location targetAt) {
        return of(target, mobAt, targetAt, 0L);
    }

    /**
     * Trend of a player from the published kinematic fields. Lateral wins over radial. An invalid word is
     * {@code STEADY}. Bits 1–43 stay unread.
     *
     * @param kinematics the packed word
     * @return the trend
     */
    static Trend trendFromKinematics(long kinematics) {
        if (!EnemyKinematics.valid(kinematics)) {
            return Trend.STEADY;
        }
        var lateral = EnemyKinematics.lateral(kinematics);
        if (lateral == EnemyKinematics.LEFT_OR_CLOSING || lateral == EnemyKinematics.RIGHT_OR_AWAY) {
            return Trend.STRAFING;
        }
        var radial = EnemyKinematics.radial(kinematics);
        if (radial == EnemyKinematics.LEFT_OR_CLOSING) {
            return Trend.CLOSING;
        }
        return radial == EnemyKinematics.RIGHT_OR_AWAY ? Trend.BACKING_AWAY : Trend.STEADY;
    }

    /**
     * Trend of an entity without keys from its velocity (M17.4, DT-36): the horizontal velocity projected on the
     * direction from the target to the mob. More than {@value #MOVING_BLOCKS_PER_TICK} blocks per tick towards the mob
     * is {@code CLOSING}, away from it {@code BACKING_AWAY}; a larger sideways part above the same speed is
     * {@code STRAFING}; anything slower is {@code STEADY}. The distance between captures corrects it afterwards, as for
     * players.
     *
     * @param towardsMobX mob x minus target x
     * @param towardsMobZ mob z minus target z
     * @param velocityX x velocity of the target, in blocks per tick
     * @param velocityZ z velocity of the target, in blocks per tick
     * @return the trend
     */
    static Trend trendFromVelocity(double towardsMobX, double towardsMobZ, double velocityX, double velocityZ) {
        var length = Math.hypot(towardsMobX, towardsMobZ);
        if (length < 1.0E-5) {
            return Trend.STEADY;
        }
        var along = (velocityX * towardsMobX + velocityZ * towardsMobZ) / length;
        var sideways = Math.abs(velocityX * towardsMobZ - velocityZ * towardsMobX) / length;
        if (sideways > MOVING_BLOCKS_PER_TICK && sideways > Math.abs(along)) {
            return Trend.STRAFING;
        }
        if (along > MOVING_BLOCKS_PER_TICK) {
            return Trend.CLOSING;
        }
        return along < -MOVING_BLOCKS_PER_TICK ? Trend.BACKING_AWAY : Trend.STEADY;
    }

    /**
     * Corrects a trend with the distance kept by the engine: a change of more than
     * {@value #DISTANCE_CHANGE_BLOCKS} block decides {@code CLOSING} or {@code BACKING_AWAY}.
     *
     * @param fromInput trend read from kinematics or velocity
     * @param currentBlocks distance now
     * @param previousBlocks distance at the previous capture
     * @return the corrected trend
     */
    static Trend correctedByDistance(Trend fromInput, double currentBlocks, double previousBlocks) {
        if (currentBlocks < previousBlocks - DISTANCE_CHANGE_BLOCKS) {
            return Trend.CLOSING;
        }
        return currentBlocks > previousBlocks + DISTANCE_CHANGE_BLOCKS ? Trend.BACKING_AWAY : fromInput;
    }
}
