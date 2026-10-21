package akyrion.thepredict.domain;

/**
 * One column of a zone, packed in a {@code long} (M24.1). There is no instance. Bits 39–63 stay zero.
 * An unknown column never yields a veto, a fact or a guard.
 */
public final class ZoneColumn {
    /** No reading. */
    public static final int UNKNOWN = 0;
    /** A floor the mob might stand on. */
    public static final int FLOOR = 1;
    /** A drop that is not the void. */
    public static final int PIT = 2;
    /** The void, or the world floor. */
    public static final int VOID = 3;

    /** Lava or a lava cauldron. Bit 22. */
    public static final int LAVA = 22;
    /** Fire or magma. */
    public static final int BURNING = 23;
    /** A block that damages on contact. */
    public static final int DAMAGING = 24;
    /** Powder snow. */
    public static final int FREEZING = 25;
    /** A block the mob treats cautiously, such as a wither rose. */
    public static final int CAUTIOUS = 26;
    /** A dripstone or similar spike. */
    public static final int SPIKES = 27;
    /** Soul sand, slime or honey. */
    public static final int SLOW = 28;
    /** Water that is not deep. */
    public static final int WATER = 29;
    /** Water the mob cannot walk. */
    public static final int DEEP_WATER = 30;
    /** A ladder or a vine. */
    public static final int CLIMBABLE = 31;
    /** A door. */
    public static final int DOOR = 32;
    /** The column sees the sky. */
    public static final int SKY_OPEN = 33;
    /** The world border. */
    public static final int BORDER = 34;
    /** Water, slime or a hay block under a drop. */
    public static final int SOFT_LANDING = 35;
    /** The two blocks under the floor are not opaque. */
    public static final int SEE_THROUGH = 36;
    /** A fence or a wall. */
    public static final int FENCE_LIKE = 37;
    /** More than one floor in the band. */
    public static final int MULTI_FLOOR = 38;

    /** Free height stored as 15 when the air is at least 15 blocks. */
    public static final int FREE_SATURATED = 15;
    /** Body height, in blocks, used by the generic queries. */
    public static final int QUERY_BODY_BLOCKS = 2;

    private ZoneColumn() {
    }

    /**
     * Packs a column. A floor outside {@code -512..511} becomes unknown.
     *
     * @param state {@link #UNKNOWN}, {@link #FLOOR}, {@link #PIT} or {@link #VOID}
     * @param floorY floor height; ignored unless the state is a floor
     * @param freeHeight air above the floor, 0–15
     * @param depth drop below the floor, 0–63
     * @param flags bits 22–38 already shifted
     * @return the packed column
     */
    public static long pack(int state, int floorY, int freeHeight, int depth, long flags) {
        var word = (long) (state & 3);
        if (state == FLOOR) {
            var stored = floorY + 512;
            if (stored < 0 || stored > 1023) {
                return UNKNOWN;
            }
            word |= (stored & 1023L) << 2;
        }
        word |= (clamp(freeHeight, 0, FREE_SATURATED) & 15L) << 12;
        word |= (clamp(depth, 0, 63) & 63L) << 16;
        return word | (flags & (((1L << 17) - 1L) << 22));
    }

    /**
     * A floor column with no flags.
     *
     * @param floorY floor height
     * @param freeHeight air above the floor
     * @return the packed column
     */
    public static long floor(int floorY, int freeHeight) {
        return pack(FLOOR, floorY, freeHeight, 0, 0L);
    }

    /**
     * State bits 0–1.
     *
     * @param column packed column
     * @return the state
     */
    public static int state(long column) {
        return (int) (column & 3L);
    }

    /**
     * Floor height. Meaningful only for {@link #FLOOR}.
     *
     * @param column packed column
     * @return the height, or 0 when the column is not a floor
     */
    public static int floorY(long column) {
        if (state(column) != FLOOR) {
            return 0;
        }
        return (int) ((column >>> 2) & 1023L) - 512;
    }

    /**
     * Air above the floor, 15 meaning at least 15.
     *
     * @param column packed column
     * @return 0–15
     */
    public static int freeHeight(long column) {
        return (int) ((column >>> 12) & 15L);
    }

    /**
     * Drop below the floor, saturated at 63.
     *
     * @param column packed column
     * @return 0–63
     */
    public static int depth(long column) {
        return (int) ((column >>> 16) & 63L);
    }

    /**
     * Whether a flag bit is set.
     *
     * @param column packed column
     * @param bit bit index, 22–38
     * @return whether that flag is set
     */
    public static boolean flag(long column, int bit) {
        return (column & (1L << bit)) != 0;
    }

    /**
     * Whether the column can be stood on by a body of the given ceiling height.
     *
     * @param column packed column
     * @param bodyBlocks ceiling of the body height, at least 1
     * @return whether it is walkable
     */
    public static boolean walkable(long column, int bodyBlocks) {
        return state(column) == FLOOR && freeHeight(column) >= bodyBlocks && !flag(column, LAVA)
                && !flag(column, DEEP_WATER) && !flag(column, FENCE_LIKE) && !flag(column, BORDER);
    }

    /**
     * Whether the column itself is a hazard. A border counts.
     *
     * @param column packed column
     * @return whether it is harmful
     */
    public static boolean harmful(long column) {
        return flag(column, LAVA) || flag(column, BURNING) || flag(column, DAMAGING) || flag(column, FREEZING)
                || flag(column, CAUTIOUS) || flag(column, BORDER);
    }

    /**
     * Whether two floors are one step apart.
     *
     * @param from packed origin
     * @param to packed destination
     * @return whether both are floors and the absolute difference is at most 1
     */
    public static boolean step(long from, long to) {
        return state(from) == FLOOR && state(to) == FLOOR && StrictMath.abs(floorY(from) - floorY(to)) <= 1;
    }

    /**
     * Sets one flag.
     *
     * @param column packed column
     * @param bit flag bit
     * @return the column with that flag set
     */
    public static long with(long column, int bit) {
        return column | (1L << bit);
    }

    private static int clamp(int value, int least, int most) {
        if (value < least) {
            return least;
        }
        return value > most ? most : value;
    }
}
