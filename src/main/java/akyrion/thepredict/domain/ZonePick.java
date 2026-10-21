package akyrion.thepredict.domain;

/**
 * One query answer packed in a {@code long} (M24.4). Cell 0–15, score 16–27, state 28–31, miss 32–35.
 */
public final class ZonePick {
    /** A cell was chosen. */
    public static final int FOUND = 1;
    /** Nothing passed the filters. */
    public static final int NONE = 2;
    /** The zone cannot answer. */
    public static final int UNKNOWN = 3;
    /** This query waits for the next tick. */
    public static final int DEFERRED = 4;

    private static final ZoneMiss[] MISSES = ZoneMiss.values();

    private ZonePick() {
    }

    /**
     * Packs an answer.
     *
     * @param cell cell index, 0–65535
     * @param score score, 0–4095
     * @param state {@link #FOUND}, {@link #NONE}, {@link #UNKNOWN} or {@link #DEFERRED}
     * @param miss why nothing was found
     * @return the packed answer
     */
    public static long pack(int cell, int score, int state, ZoneMiss miss) {
        var word = (long) (cell & 65535);
        word |= (score & 4095L) << 16;
        word |= (state & 15L) << 28;
        word |= (miss.ordinal() & 15L) << 32;
        return word;
    }

    /**
     * An unknown answer.
     *
     * @return the packed answer
     */
    public static long unknown() {
        return pack(0, 0, UNKNOWN, ZoneMiss.NONE);
    }

    /**
     * A deferred answer.
     *
     * @return the packed answer
     */
    public static long deferred() {
        return pack(0, 0, DEFERRED, ZoneMiss.NONE);
    }

    /**
     * A miss.
     *
     * @param miss the reason
     * @return the packed answer
     */
    public static long none(ZoneMiss miss) {
        return pack(0, 0, NONE, miss);
    }

    /**
     * The cell index.
     *
     * @param pick packed answer
     * @return 0–65535
     */
    public static int cell(long pick) {
        return (int) (pick & 65535L);
    }

    /**
     * The score.
     *
     * @param pick packed answer
     * @return 0–4095
     */
    public static int score(long pick) {
        return (int) ((pick >>> 16) & 4095L);
    }

    /**
     * The state.
     *
     * @param pick packed answer
     * @return the state code
     */
    public static int state(long pick) {
        return (int) ((pick >>> 28) & 15L);
    }

    /**
     * The miss.
     *
     * @param pick packed answer
     * @return the reason
     */
    public static ZoneMiss miss(long pick) {
        var ordinal = (int) ((pick >>> 32) & 15L);
        return ordinal < MISSES.length ? MISSES[ordinal] : ZoneMiss.NONE;
    }
}
