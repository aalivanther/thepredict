package akyrion.thepredict.domain;

/**
 * Packed stance of one mob (M23.1). Bits 11–63 stay zero. Deadlines live beside this word, not inside it.
 */
public final class StanceState {
    /** Base stance, bits 0–1. */
    public static final int BASE_MASK = 0b11;
    /** Pending base, bits 2–3. */
    public static final int PENDING_SHIFT = 2;
    /** Action stance, bits 4–5. */
    public static final int ACTION_SHIFT = 4;
    /** Enraged mark, bit 6. */
    public static final int ENRAGED_BIT = 6;
    /** A base is waiting, bit 7. */
    public static final int PENDING_FLAG = 7;
    /** Consecutive reactive cycles of the same pending base, bits 8–10. */
    public static final int CYCLES_SHIFT = 8;
    /** Neutral, no action, not enraged. */
    public static final long INITIAL = 0L;

    private StanceState() {
    }

    /**
     * Base ordinal.
     *
     * @param state packed state
     * @return 0–3
     */
    public static int base(long state) {
        return (int) (state & BASE_MASK);
    }

    /**
     * Pending base ordinal.
     *
     * @param state packed state
     * @return 0–3
     */
    public static int pendingBase(long state) {
        return (int) ((state >>> PENDING_SHIFT) & BASE_MASK);
    }

    /**
     * Action ordinal.
     *
     * @param state packed state
     * @return 0–3
     */
    public static int action(long state) {
        return (int) ((state >>> ACTION_SHIFT) & BASE_MASK);
    }

    /**
     * Whether enraged is set.
     *
     * @param state packed state
     * @return the mark
     */
    public static boolean enraged(long state) {
        return ((state >>> ENRAGED_BIT) & 1L) != 0L;
    }

    /**
     * Whether a base change is waiting.
     *
     * @param state packed state
     * @return the flag
     */
    public static boolean pending(long state) {
        return ((state >>> PENDING_FLAG) & 1L) != 0L;
    }

    /**
     * Cycles the pending base has already held.
     *
     * @param state packed state
     * @return 0–7
     */
    public static int cycles(long state) {
        return (int) ((state >>> CYCLES_SHIFT) & 0b111L);
    }

    /**
     * Visible stance.
     *
     * @param state packed state
     * @return the combat stance
     */
    public static CombatStance visible(long state) {
        return CombatStance.visible(base(state), action(state));
    }

    /**
     * Replaces the base and clears the pending bits.
     *
     * @param state packed state
     * @param baseOrdinal new base
     * @return the word
     */
    public static long applyBase(long state, int baseOrdinal) {
        long cleared = state & ~((BASE_MASK << PENDING_SHIFT) | (1L << PENDING_FLAG) | (0b111L << CYCLES_SHIFT) | BASE_MASK);
        return cleared | (baseOrdinal & BASE_MASK);
    }

    /**
     * Stores a pending base and its cycle count.
     *
     * @param state packed state
     * @param baseOrdinal pending base
     * @param cycles cycles already counted, clamped to 0–7
     * @return the word
     */
    public static long pend(long state, int baseOrdinal, int cycles) {
        long cleared = state & ~((BASE_MASK << PENDING_SHIFT) | (1L << PENDING_FLAG) | (0b111L << CYCLES_SHIFT));
        int held = Math.min(7, Math.max(0, cycles));
        return cleared | ((long) (baseOrdinal & BASE_MASK) << PENDING_SHIFT) | (1L << PENDING_FLAG)
                | ((long) held << CYCLES_SHIFT);
    }

    /**
     * Clears a pending base.
     *
     * @param state packed state
     * @return the word
     */
    public static long clearPending(long state) {
        return state & ~((BASE_MASK << PENDING_SHIFT) | (1L << PENDING_FLAG) | (0b111L << CYCLES_SHIFT));
    }

    /**
     * Replaces the action bits.
     *
     * @param state packed state
     * @param actionOrdinal action
     * @return the word
     */
    public static long withAction(long state, int actionOrdinal) {
        return (state & ~(BASE_MASK << ACTION_SHIFT)) | ((long) (actionOrdinal & BASE_MASK) << ACTION_SHIFT);
    }

    /**
     * Sets or clears enraged.
     *
     * @param state packed state
     * @param enraged the mark
     * @return the word
     */
    public static long withEnraged(long state, boolean enraged) {
        return enraged ? state | (1L << ENRAGED_BIT) : state & ~(1L << ENRAGED_BIT);
    }
}
