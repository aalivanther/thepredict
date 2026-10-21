package akyrion.thepredict.engine;

/**
 * How much a {@link DecisionTap} wants built (M21.1). Independent of whether a duel is attached.
 */
public enum TapDetail {
    /** Nothing is built. */
    NONE,
    /** Decision lines only. */
    DECISIONS,
    /** Decision lines plus casts, threat, reactions and micro. */
    FULL;

    /**
     * The greater of two details.
     *
     * @param left one detail
     * @param right the other
     * @return the maximum
     */
    public static TapDetail max(TapDetail left, TapDetail right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
