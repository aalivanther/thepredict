package akyrion.thepredict.domain;

/**
 * Bit layout of one per-tick reading (M20.2). Readers are static and allocate nothing. v2 stores four push dangers in
 * bits 44–51: the seven-argument pack writes {@link PushDanger#UNKNOWN} in all four, and the eleven-argument pack
 * writes the given codes. v3 adds the target's movement bits 52–55, which only the flags carry.
 */
public final class TickSignals {
    /** The mob is alive in the session world. */
    public static final int VALID = 0;
    /** The expected target is alive, in the same world and admitted. */
    public static final int HAS_TARGET = 1;
    /** The live target is not the expected one. */
    public static final int TARGET_MISMATCH = 2;
    /** Gap in quarter-blocks, bits 3–9, saturated at 31.75. */
    public static final int GAP_SHIFT = 3;
    /** {@code |dy| > 1.5}. */
    public static final int VERTICAL_GAP = 10;
    /** Octant of the mob around the target's look, bits 11–13, 0 in front and 4 behind. */
    public static final int OCTANT_SHIFT = 11;
    /** Facing error of the mob toward the target, bits 14–16. */
    public static final int FACING_SHIFT = 14;
    /** Ticks until the target can be hit, bits 17–21. */
    public static final int HITTABLE_SHIFT = 17;
    /** The target is blocking. */
    public static final int TARGET_BLOCKING = 22;
    /** A bow is drawn or a crossbow is loaded. */
    public static final int TARGET_SHOT_READY = 23;
    /** A player's attack cooldown is below 0.9. */
    public static final int TARGET_ATTACK_RECHARGING = 24;
    /** The target is eating or drinking. */
    public static final int TARGET_CONSUMING = 25;
    /** A projectile from the expected target is still in flight toward this mob. */
    public static final int INCOMING_PROJECTILE = 26;
    /** The mob is on the ground. */
    public static final int MOB_ON_GROUND = 27;
    /** The mob is inside its own hurt window. */
    public static final int MOB_STAGGERED = 28;
    /** Mob health percent, bits 29–35, clamped to 100. */
    public static final int MOB_HEALTH_SHIFT = 29;
    /** Target health percent, bits 36–42. */
    public static final int TARGET_HEALTH_SHIFT = 36;
    /** The mob is to the left of the target's look. */
    public static final int MOB_LEFT_OF_TARGET = 43;
    /** Push danger under the mob, bits 44–45. */
    public static final int MOB_PUSH_NEAR = 44;
    /** Push danger behind the mob, bits 46–47. */
    public static final int MOB_PUSH_FAR = 46;
    /** Push danger beside the target, bits 48–49. */
    public static final int TARGET_PUSH_NEAR = 48;
    /** Push danger on the target's far side, bits 50–51. */
    public static final int TARGET_PUSH_FAR = 50;
    /** The target is airborne. Bit 52 of v3. */
    public static final int TARGET_AIRBORNE = 52;
    /** The target is sprinting. Bit 53 of v3. */
    public static final int TARGET_SPRINTING = 53;
    /** The target is raising a shield. Bit 54 of v3. */
    public static final int TARGET_RAISING = 54;
    /** The target is falling faster than 1.5. Bit 55 of v3. */
    public static final int TARGET_FALLING_FAST = 55;

    /** A reading that only says the mob is present, with {@code h = 0}. */
    public static final long PRESENT = 1L;

    private TickSignals() {
    }

    /**
     * Whether a bit is set.
     *
     * @param signals packed word
     * @param index bit index
     * @return whether that bit is set
     */
    public static boolean bit(long signals, int index) {
        return (signals & (1L << index)) != 0;
    }

    /**
     * Gap in quarter-blocks, 0–127.
     *
     * @param signals packed word
     * @return the gap field
     */
    public static int gapQuarters(long signals) {
        return (int) ((signals >>> GAP_SHIFT) & 127L);
    }

    /**
     * Octant 0–4 of the mob around the target's look.
     *
     * @param signals packed word
     * @return the octant field
     */
    public static int octant(long signals) {
        return (int) ((signals >>> OCTANT_SHIFT) & 7L);
    }

    /**
     * Facing error of the mob, in octants 0–4.
     *
     * @param signals packed word
     * @return the facing field
     */
    public static int facingError(long signals) {
        return (int) ((signals >>> FACING_SHIFT) & 7L);
    }

    /**
     * Ticks until the target can be hit, 0–31.
     *
     * @param signals packed word
     * @return the hittable field
     */
    public static int hittableTicks(long signals) {
        return (int) ((signals >>> HITTABLE_SHIFT) & 31L);
    }

    /**
     * Health percent of the mob, 0–100.
     *
     * @param signals packed word
     * @return the mob health field
     */
    public static int mobHealthPercent(long signals) {
        return (int) ((signals >>> MOB_HEALTH_SHIFT) & 127L);
    }

    /**
     * Health percent of the target, 0–100.
     *
     * @param signals packed word
     * @return the target health field
     */
    public static int targetHealthPercent(long signals) {
        return (int) ((signals >>> TARGET_HEALTH_SHIFT) & 127L);
    }

    /**
     * Quarter-blocks of a horizontal gap. A negative gap (overlapping boxes) is 0; 31.75 and above saturate at 127.
     *
     * @param gapBlocks gap after subtracting half the widths
     * @return 0–127
     */
    public static int quartersOf(double gapBlocks) {
        if (!(gapBlocks > 0.0)) {
            return 0;
        }
        var quarters = (int) (gapBlocks * 4.0);
        return quarters > 127 ? 127 : quarters;
    }

    /**
     * Health percent, floored and clamped to 100. Absorption may exceed the maximum.
     *
     * @param health current health
     * @param absorption current absorption
     * @param maximum maximum health
     * @return 0–100
     */
    public static int percent(double health, double absorption, double maximum) {
        if (!(maximum > 0.0)) {
            return 0;
        }
        var raw = (int) ((health + absorption) / maximum * 100.0);
        if (raw < 0) {
            return 0;
        }
        return raw > 100 ? 100 : raw;
    }

    /**
     * Folded octant of {@code (dx, dz)} around the look {@code (lookX, lookZ)}: 0 within 22.5° of the look, then one
     * step per 45°, and 4 from 157.5° on, behind (M20, bits 11–13). Zero when either vector vanishes.
     *
     * @param lookX look component on X
     * @param lookZ look component on Z
     * @param dx world delta X
     * @param dz world delta Z
     * @return 0 in front through 4 behind
     */
    public static int octantOf(double lookX, double lookZ, double dx, double dz) {
        var look = StrictMath.hypot(lookX, lookZ);
        var length = StrictMath.hypot(dx, dz);
        if (look == 0.0 || length == 0.0) {
            return 0;
        }
        var dot = (lookX * dx + lookZ * dz) / (look * length);
        if (dot > 1.0) {
            dot = 1.0;
        } else if (dot < -1.0) {
            dot = -1.0;
        }
        var degrees = StrictMath.toDegrees(StrictMath.acos(dot));
        var octant = (int) StrictMath.floor((degrees + 22.5) / 45.0);
        return octant > 4 ? 4 : octant;
    }

    /**
     * Whether {@code (dx, dz)} is to the left of the look. The cross {@code lookX * dz - lookZ * dx} is negative on
     * the left when the look faces +Z and right is −X.
     *
     * @param lookX look component on X
     * @param lookZ look component on Z
     * @param dx world delta X from the looker
     * @param dz world delta Z from the looker
     * @return whether the point is on the left
     */
    public static boolean leftOf(double lookX, double lookZ, double dx, double dz) {
        return lookX * dz - lookZ * dx < 0.0;
    }

    /**
     * Look vector of a Minecraft yaw: 0 faces +Z and the yaw grows toward −X.
     *
     * @param yawDegrees entity yaw
     * @return {@code x} of the horizontal look; the Z component is {@link StrictMath#cos}
     */
    public static double lookX(float yawDegrees) {
        return -StrictMath.sin(StrictMath.toRadians(yawDegrees));
    }

    /**
     * Z component of {@link #lookX(float)}.
     *
     * @param yawDegrees entity yaw
     * @return the Z component
     */
    public static double lookZ(float yawDegrees) {
        return StrictMath.cos(StrictMath.toRadians(yawDegrees));
    }

    /**
     * Packs one reading. Widths of the reserved bits are masked.
     *
     * @param flags bits 0–2, 10, 22–28 and 43 already shifted into place; other bits are ignored
     * @param gapQuarters 0–127
     * @param octant 0–4
     * @param facing 0–4
     * @param hittable 0–31
     * @param mobHealth 0–100
     * @param targetHealth 0–100
     * @return the packed word
     */
    public static long pack(long flags, int gapQuarters, int octant, int facing, int hittable, int mobHealth,
                            int targetHealth) {
        gapQuarters = clamp(gapQuarters, 0, 127);
        octant = clamp(octant, 0, 4);
        facing = clamp(facing, 0, 4);
        hittable = clamp(hittable, 0, 31);
        mobHealth = clamp(mobHealth, 0, 100);
        targetHealth = clamp(targetHealth, 0, 100);
        var word = flags & ~((127L << GAP_SHIFT) | (7L << OCTANT_SHIFT) | (7L << FACING_SHIFT)
                | (31L << HITTABLE_SHIFT) | (127L << MOB_HEALTH_SHIFT) | (127L << TARGET_HEALTH_SHIFT));
        word |= (gapQuarters & 127L) << GAP_SHIFT;
        word |= (octant & 7L) << OCTANT_SHIFT;
        word |= (facing & 7L) << FACING_SHIFT;
        word |= (hittable & 31L) << HITTABLE_SHIFT;
        word |= (mobHealth & 127L) << MOB_HEALTH_SHIFT;
        word |= (targetHealth & 127L) << TARGET_HEALTH_SHIFT;
        var unknown = (long) PushDanger.UNKNOWN.ordinal();
        word |= unknown << MOB_PUSH_NEAR;
        word |= unknown << MOB_PUSH_FAR;
        word |= unknown << TARGET_PUSH_NEAR;
        word |= unknown << TARGET_PUSH_FAR;
        return word;
    }

    /**
     * Packs one reading and four push dangers. Codes are the ordinals of {@link PushDanger}.
     *
     * @param flags bits 0–2, 10, 22–28 and 43 already shifted into place
     * @param gapQuarters 0–127
     * @param octant 0–4
     * @param facing 0–4
     * @param hittable 0–31
     * @param mobHealth 0–100
     * @param targetHealth 0–100
     * @param mobNear push under the mob
     * @param mobFar push behind the mob
     * @param targetNear push beside the target
     * @param targetFar push on the target's far side
     * @return the packed word
     */
    public static long pack(long flags, int gapQuarters, int octant, int facing, int hittable, int mobHealth,
                            int targetHealth, int mobNear, int mobFar, int targetNear, int targetFar) {
        var word = pack(flags, gapQuarters, octant, facing, hittable, mobHealth, targetHealth);
        word &= ~((3L << MOB_PUSH_NEAR) | (3L << MOB_PUSH_FAR) | (3L << TARGET_PUSH_NEAR) | (3L << TARGET_PUSH_FAR));
        word |= (mobNear & 3L) << MOB_PUSH_NEAR;
        word |= (mobFar & 3L) << MOB_PUSH_FAR;
        word |= (targetNear & 3L) << TARGET_PUSH_NEAR;
        word |= (targetFar & 3L) << TARGET_PUSH_FAR;
        return word;
    }

    /**
     * One two-bit push field.
     *
     * @param signals packed word
     * @param shift {@link #MOB_PUSH_NEAR}, {@link #MOB_PUSH_FAR}, {@link #TARGET_PUSH_NEAR} or
     *        {@link #TARGET_PUSH_FAR}
     * @return 0–3
     */
    public static int push(long signals, int shift) {
        return (int) ((signals >>> shift) & 3L);
    }

    private static int clamp(int value, int least, int most) {
        if (value < least) {
            return least;
        }
        return value > most ? most : value;
    }
}
