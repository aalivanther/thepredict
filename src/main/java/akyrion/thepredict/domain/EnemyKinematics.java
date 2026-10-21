package akyrion.thepredict.domain;

/**
 * Readers of the kinematic word v1 (M26.1). The stamp in bits 1–43 stays opaque: this type never exposes it.
 */
public final class EnemyKinematics {
    /** The reading is present. */
    public static final int VALID = 0;
    /** Lateral field, bits 44–45. */
    public static final int LATERAL_SHIFT = 44;
    /** Radial field, bits 46–47. */
    public static final int RADIAL_SHIFT = 46;
    /** The enemy is airborne ({@code getFallDistance() > 0}). */
    public static final int AIRBORNE = 48;
    /** The enemy is sprinting. */
    public static final int SPRINTING = 49;
    /** The enemy is invisible. */
    public static final int INVISIBLE = 50;
    /** No lateral or radial motion. */
    public static final int NONE = 0;
    /** Lateral left, or radial closing. */
    public static final int LEFT_OR_CLOSING = 1;
    /** Lateral right, or radial backing away. */
    public static final int RIGHT_OR_AWAY = 2;

    private EnemyKinematics() {
    }

    /**
     * Whether the word is valid.
     *
     * @param word the packed reading
     * @return bit 0
     */
    public static boolean valid(long word) {
        return (word & 1L) != 0L;
    }

    /**
     * Lateral code: 0 none, 1 left, 2 right.
     *
     * @param word the packed reading
     * @return bits 44–45
     */
    public static int lateral(long word) {
        return (int) ((word >>> LATERAL_SHIFT) & 3L);
    }

    /**
     * Radial code against the witness: 0 none or no witness, 1 closing, 2 away.
     *
     * @param word the packed reading
     * @return bits 46–47
     */
    public static int radial(long word) {
        return (int) ((word >>> RADIAL_SHIFT) & 3L);
    }

    /**
     * Whether the enemy is airborne.
     *
     * @param word the packed reading
     * @return bit 48
     */
    public static boolean airborne(long word) {
        return bit(word, AIRBORNE);
    }

    /**
     * Whether the enemy is sprinting.
     *
     * @param word the packed reading
     * @return bit 49
     */
    public static boolean sprinting(long word) {
        return bit(word, SPRINTING);
    }

    /**
     * Whether the enemy is invisible.
     *
     * @param word the packed reading
     * @return bit 50
     */
    public static boolean invisible(long word) {
        return bit(word, INVISIBLE);
    }

    private static boolean bit(long word, int index) {
        return (word & (1L << index)) != 0L;
    }
}
