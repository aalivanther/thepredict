package akyrion.thepredict.domain;

/**
 * Bits of one material in the zone table (M24.1). A {@code short} holds bits 0–15. The adapter fills the table;
 * the domain only names the bits. See-through is computed at scan time, not stored here.
 */
public final class ZoneMaterialFlags {
    /** The block has a collision shape that is not a fluid. */
    public static final short SOLID = 1;
    /** The block hides what is behind it. */
    public static final short OCCLUDING = 1 << 1;
    /** The block collides with an entity. */
    public static final short COLLIDABLE = 1 << 2;
    /** Lava or a lava cauldron. */
    public static final short LAVA = 1 << 3;
    /** Water, including a bubble column and kelp. */
    public static final short WATER = 1 << 4;
    /** Fire, a lit campfire or magma. */
    public static final short BURNING = 1 << 5;
    /** Cactus, sweet berries or a wither rose. */
    public static final short DAMAGING = 1 << 6;
    /** Powder snow. */
    public static final short FREEZING = 1 << 7;
    /** A wither rose. */
    public static final short CAUTIOUS = 1 << 8;
    /** Pointed dripstone. */
    public static final short SPIKES = 1 << 9;
    /** Soul sand, slime or honey. */
    public static final short SLOW = 1 << 10;
    /** A ladder, a vine or similar. */
    public static final short CLIMBABLE = 1 << 11;
    /** A door, a gate or a trapdoor. */
    public static final short DOOR = 1 << 12;
    /** Water, slime or hay, which cancel fall damage. */
    public static final short SOFT = 1 << 13;
    /** A fence or a wall. */
    public static final short FENCE = 1 << 14;
    /** Cobweb. */
    public static final short WEB = (short) (1 << 15);

    private ZoneMaterialFlags() {
    }

    /**
     * Whether a bit is set.
     *
     * @param flags packed flags
     * @param bit one flag
     * @return whether it is set
     */
    public static boolean has(short flags, short bit) {
        return (flags & bit) != 0;
    }
}
