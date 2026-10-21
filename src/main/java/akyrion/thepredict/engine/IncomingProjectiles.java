package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.UUID;

/**
 * Shots still in flight toward a mob (M20.2). Fixed capacity 256; a newer shot evicts the oldest. The listener
 * writes and the signal reader asks, both on the server thread, so the shots are recorded in the order of their tick
 * and the walk from the newest one stops at the first shot that even the slowest stored speed could not keep in the
 * air (SP-19): a read costs the fresh shots, not the whole ring.
 */
public final class IncomingProjectiles {
    /** Slots in the ring. */
    public static final int CAPACITY = 256;
    /** Degrees of aim that still count as aimed at the mob. */
    public static final double CONE_DEGREES = 15.0;

    private final UUID[] shooters = new UUID[CAPACITY];
    private final long[] ticks = new long[CAPACITY];
    private final float[] yaws = new float[CAPACITY];
    private final double[] speeds = new double[CAPACITY];
    private int next;
    private int filled;
    private double slowest;

    /**
     * Empty ring. The launch listener and the signal reader share one instance.
     */
    public IncomingProjectiles() {
    }

    /**
     * Records one launch.
     *
     * @param shooter who fired
     * @param tick server tick of the launch
     * @param aimYawDegrees horizontal aim
     * @param speedBlocksPerTick speed; a non-positive speed is ignored
     */
    public void launched(UUID shooter, long tick, float aimYawDegrees, double speedBlocksPerTick) {
        Objects.requireNonNull(shooter, "shooter");
        if (!(speedBlocksPerTick > 0.0)) {
            return;
        }
        var full = filled == CAPACITY;
        shooters[next] = shooter;
        ticks[next] = tick;
        yaws[next] = aimYawDegrees;
        speeds[next] = speedBlocksPerTick;
        next = (next + 1) % CAPACITY;
        if (filled < CAPACITY) {
            filled++;
        }
        slowest = full ? slowestStored() : filled == 1 ? speedBlocksPerTick : Math.min(slowest, speedBlocksPerTick);
    }

    /** The slowest speed in the ring, read again when a launch evicted a shot. A launch is not a per-tick event. */
    private double slowestStored() {
        var found = speeds[0];
        for (var index = 1; index < CAPACITY; index++) {
            found = Math.min(found, speeds[index]);
        }
        return found;
    }

    /**
     * How many shots are remembered.
     *
     * @return the number of shots still in the ring
     */
    public int size() {
        return filled;
    }

    /**
     * Whether this shooter still has a shot aimed at the mob.
     *
     * @param shooter expected target
     * @param now current tick
     * @param distanceBlocks horizontal distance
     * @param towardYawDegrees yaw from the shooter toward the mob
     * @return whether the flight time and the cone both hold
     */
    public boolean recent(UUID shooter, long now, double distanceBlocks, double towardYawDegrees) {
        var longest = filled == 0 ? 0L : (long) Math.ceil(distanceBlocks / slowest) + 2L;
        for (var offset = 0; offset < filled; offset++) {
            var index = Math.floorMod(next - 1 - offset, CAPACITY);
            if (now - ticks[index] > longest) {
                return false;
            }
            if (!shooter.equals(shooters[index])) {
                continue;
            }
            var flight = (long) Math.ceil(distanceBlocks / speeds[index]) + 2L;
            var age = now - ticks[index];
            if (age < 0 || age > flight) {
                continue;
            }
            var delta = StrictMath.IEEEremainder(yaws[index] - towardYawDegrees, 360.0);
            if (delta <= CONE_DEGREES && delta >= -CONE_DEGREES) {
                return true;
            }
        }
        return false;
    }
}
