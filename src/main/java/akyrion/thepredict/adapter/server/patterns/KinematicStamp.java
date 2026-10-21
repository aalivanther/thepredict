package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.EnemyKinematics;

/**
 * Packs and reads the opaque stamp of kinematic word v1 (M26.1). Bits 1–43 are X and Z in quarter-blocks modulo
 * 2^20 and the yaw octant. Only this class interprets them. A jump of more than 8 blocks in 2 ticks is a new stamp
 * and produces no movement token.
 */
public final class KinematicStamp {
    /** Quarter-blocks of one coordinate, modulo this. */
    public static final int MODULUS = 1 << 20;
    /** Blocks that count as a teleport. */
    public static final double TELEPORT_BLOCKS = 8.0;
    private static final int X_SHIFT = 1;
    private static final int Z_SHIFT = 21;
    private static final int YAW_SHIFT = 41;

    private KinematicStamp() {
    }

    /**
     * Packs one reading. The stamp is new when the previous word is invalid or the enemy teleported.
     *
     * @param x block x
     * @param z block z
     * @param yawDegrees yaw in degrees
     * @param previous previous word, zero when there is none
     * @param lateral 0, 1 or 2
     * @param radial 0, 1 or 2
     * @param airborne fall distance is positive
     * @param sprinting the enemy is sprinting
     * @param invisible the enemy is invisible
     * @param gapTicks ticks since the previous sample
     * @return the word, with lateral and radial cleared when this sample is a teleport
     */
    public static long pack(double x, double z, float yawDegrees, long previous, int lateral, int radial,
                            boolean airborne, boolean sprinting, boolean invisible, int gapTicks) {
        var teleported = EnemyKinematics.valid(previous) && gapTicks <= 2 && distance(previous, x, z) > TELEPORT_BLOCKS;
        var word = 1L;
        word |= (quarters(x) & (MODULUS - 1L)) << X_SHIFT;
        word |= (quarters(z) & (MODULUS - 1L)) << Z_SHIFT;
        word |= (octant(yawDegrees) & 7L) << YAW_SHIFT;
        if (!teleported) {
            word |= (lateral & 3L) << EnemyKinematics.LATERAL_SHIFT;
            word |= (radial & 3L) << EnemyKinematics.RADIAL_SHIFT;
        }
        if (airborne) {
            word |= 1L << EnemyKinematics.AIRBORNE;
        }
        if (sprinting) {
            word |= 1L << EnemyKinematics.SPRINTING;
        }
        if (invisible) {
            word |= 1L << EnemyKinematics.INVISIBLE;
        }
        return word;
    }

    /**
     * Whether this sample jumped more than {@link #TELEPORT_BLOCKS} inside two ticks.
     *
     * @param previous previous word
     * @param x block x
     * @param z block z
     * @param gapTicks ticks since the previous sample
     * @return {@code true} when movement tokens must be suppressed
     */
    public static boolean teleported(long previous, double x, double z, int gapTicks) {
        return EnemyKinematics.valid(previous) && gapTicks <= 2 && distance(previous, x, z) > TELEPORT_BLOCKS;
    }

    /**
     * Quarter-block x stored in a word.
     *
     * @param word the word
     * @return the field
     */
    public static int xQuarters(long word) {
        return (int) ((word >>> X_SHIFT) & (MODULUS - 1L));
    }

    /**
     * Quarter-block z stored in a word.
     *
     * @param word the word
     * @return the field
     */
    public static int zQuarters(long word) {
        return (int) ((word >>> Z_SHIFT) & (MODULUS - 1L));
    }

    /**
     * Wrapped distance in blocks between a stamp and a position.
     *
     * @param word the previous word
     * @param x block x
     * @param z block z
     * @return the horizontal distance
     */
    public static double distance(long word, double x, double z) {
        var dx = deltaQuarters(x, xQuarters(word));
        var dz = deltaQuarters(z, zQuarters(word));
        return StrictMath.hypot(dx / 4.0, dz / 4.0);
    }

    /**
     * Signed displacement, in quarter-blocks, from a stored coordinate to a position. Both sides are quantized the
     * same way and the difference is unwrapped around the modulus, so a negative coordinate or a wrap at 2^18 blocks
     * never reads as movement (SP-01).
     *
     * @param blocks the current coordinate
     * @param storedQuarters the field of the previous word
     * @return quarter-blocks moved, positive towards +X or +Z
     */
    public static int deltaQuarters(double blocks, int storedQuarters) {
        return wrapped(quarters(blocks) - storedQuarters);
    }

    /**
     * Lateral and radial codes from a displacement. Lateral wins when it is strictly larger.
     *
     * @param dx block delta of the enemy
     * @param dz block delta of the enemy
     * @param towardsWitnessX witness x minus enemy x
     * @param towardsWitnessZ witness z minus enemy z
     * @param strafeBlocks minimum movement
     * @param witnessPresent whether a witness exists
     * @return bits, lateral in the low 2 and radial in the next 2
     */
    public static int motion(double dx, double dz, double towardsWitnessX, double towardsWitnessZ,
                             double strafeBlocks, boolean witnessPresent) {
        var length = StrictMath.hypot(towardsWitnessX, towardsWitnessZ);
        var along = length < 1.0e-5 || !witnessPresent ? 0.0
                : (dx * towardsWitnessX + dz * towardsWitnessZ) / length;
        var side = length < 1.0e-5 ? StrictMath.hypot(dx, dz)
                : (dx * towardsWitnessZ - dz * towardsWitnessX) / length;
        var lateral = 0;
        var radial = 0;
        if (StrictMath.abs(side) >= strafeBlocks) {
            lateral = side > 0.0 ? EnemyKinematics.LEFT_OR_CLOSING : EnemyKinematics.RIGHT_OR_AWAY;
        }
        if (witnessPresent && StrictMath.abs(along) >= strafeBlocks) {
            radial = along > 0.0 ? EnemyKinematics.LEFT_OR_CLOSING : EnemyKinematics.RIGHT_OR_AWAY;
        }
        if (StrictMath.abs(side) > StrictMath.abs(along)) {
            radial = 0;
        } else if (StrictMath.abs(along) > StrictMath.abs(side)) {
            lateral = 0;
        }
        return lateral | (radial << 2);
    }

    private static int quarters(double blocks) {
        var quarters = (int) StrictMath.floor(blocks * 4.0);
        var wrapped = quarters % MODULUS;
        return wrapped < 0 ? wrapped + MODULUS : wrapped;
    }

    private static int wrapped(int deltaQuarters) {
        var delta = deltaQuarters % MODULUS;
        if (delta > MODULUS / 2) {
            delta -= MODULUS;
        } else if (delta < -MODULUS / 2) {
            delta += MODULUS;
        }
        return delta;
    }

    private static int octant(float yawDegrees) {
        var wrapped = yawDegrees % 360.0f;
        if (wrapped < 0.0f) {
            wrapped += 360.0f;
        }
        return (int) (wrapped / 45.0f) & 7;
    }
}
