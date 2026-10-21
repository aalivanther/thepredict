package akyrion.thepredict.domain;

/**
 * Short destinations in blocks (M20.5). Pure. The adapter reads the numbers and calls the pathfinder.
 *
 * @param x world X
 * @param z world Z
 */
public record Steering(double x, double z) {
    /** Step of an orbit, in blocks. */
    public static final double ORBIT_STEP = 1.5;
    /** Length of a sidestep or a backstep, in blocks. */
    public static final double STEP_BLOCKS = 2.0;

    /**
     * Orbit destination: the vector from the target to the mob, rotated by {@code 1.5 / R} and clamped to the band
     * and the arena. A mob standing on the target starts from the east, as a sidestep does, so the point stays finite.
     *
     * @param mobX mob X
     * @param mobZ mob Z
     * @param targetX target X
     * @param targetZ target Z
     * @param side which way to rotate
     * @param bandInner inner radius
     * @param bandOuter outer radius
     * @param arenaX arena centre X
     * @param arenaZ arena centre Z
     * @param arenaRadius arena radius
     * @return the destination
     */
    public static Steering orbit(double mobX, double mobZ, double targetX, double targetZ, MicroSide side,
                                 double bandInner, double bandOuter, double arenaX, double arenaZ,
                                 double arenaRadius) {
        var dx = mobX - targetX;
        var dz = mobZ - targetZ;
        var radius = StrictMath.hypot(dx, dz);
        var clamped = clamp(radius, bandInner, bandOuter);
        if (clamped == 0.0) {
            return new Steering(targetX, targetZ);
        }
        if (radius == 0.0) {
            dx = clamped;
            dz = 0.0;
        }
        var theta = ORBIT_STEP / clamped * (side == MicroSide.LEFT ? 1.0 : -1.0);
        var cosine = StrictMath.cos(theta);
        var sine = StrictMath.sin(theta);
        var rotatedX = dx * cosine - dz * sine;
        var rotatedZ = dx * sine + dz * cosine;
        var length = StrictMath.hypot(rotatedX, rotatedZ);
        var point = new Steering(targetX + rotatedX / length * clamped, targetZ + rotatedZ / length * clamped);
        return inside(point, arenaX, arenaZ, arenaRadius);
    }

    /**
     * Two blocks perpendicular to the line from the target to the mob.
     *
     * @param mobX mob X
     * @param mobZ mob Z
     * @param targetX target X
     * @param targetZ target Z
     * @param side which perpendicular
     * @return the destination
     */
    public static Steering sidestep(double mobX, double mobZ, double targetX, double targetZ, MicroSide side) {
        var dx = mobX - targetX;
        var dz = mobZ - targetZ;
        var length = StrictMath.hypot(dx, dz);
        if (length == 0.0) {
            return new Steering(mobX + (side == MicroSide.LEFT ? STEP_BLOCKS : -STEP_BLOCKS), mobZ);
        }
        var sign = side == MicroSide.LEFT ? 1.0 : -1.0;
        return new Steering(mobX + sign * (-dz) / length * STEP_BLOCKS, mobZ + sign * dx / length * STEP_BLOCKS);
    }

    /**
     * Two blocks away from the target.
     *
     * @param mobX mob X
     * @param mobZ mob Z
     * @param targetX target X
     * @param targetZ target Z
     * @return the destination
     */
    public static Steering backstep(double mobX, double mobZ, double targetX, double targetZ) {
        var dx = mobX - targetX;
        var dz = mobZ - targetZ;
        var length = StrictMath.hypot(dx, dz);
        if (length == 0.0) {
            return new Steering(mobX, mobZ + STEP_BLOCKS);
        }
        return new Steering(mobX + dx / length * STEP_BLOCKS, mobZ + dz / length * STEP_BLOCKS);
    }

    /**
     * The point on the line from the target to the mob at {@code reach + 0.5}.
     *
     * @param mobX mob X
     * @param mobZ mob Z
     * @param targetX target X
     * @param targetZ target Z
     * @param reachBlocks reach of the target
     * @return the destination
     */
    public static Steering hover(double mobX, double mobZ, double targetX, double targetZ, double reachBlocks) {
        var dx = mobX - targetX;
        var dz = mobZ - targetZ;
        var length = StrictMath.hypot(dx, dz);
        var distance = reachBlocks + 0.5;
        if (length == 0.0) {
            return new Steering(targetX, targetZ + distance);
        }
        return new Steering(targetX + dx / length * distance, targetZ + dz / length * distance);
    }

    /**
     * Speed of an enter: the approach speed times 1.1.
     *
     * @param approachSpeed approach speed of the profile
     * @return the speed
     */
    public static double enterSpeed(double approachSpeed) {
        return approachSpeed * 1.1;
    }

    private static Steering inside(Steering point, double arenaX, double arenaZ, double arenaRadius) {
        var dx = point.x - arenaX;
        var dz = point.z - arenaZ;
        var fromCenter = StrictMath.hypot(dx, dz);
        if (arenaRadius <= 0.0 || fromCenter <= arenaRadius) {
            return point;
        }
        return new Steering(arenaX + dx / fromCenter * arenaRadius, arenaZ + dz / fromCenter * arenaRadius);
    }

    private static double clamp(double value, double low, double high) {
        if (value < low) {
            return low;
        }
        return value > high ? high : value;
    }
}
