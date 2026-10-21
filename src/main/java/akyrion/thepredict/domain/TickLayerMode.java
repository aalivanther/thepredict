package akyrion.thepredict.domain;

/**
 * How much of the per-tick layer a profile runs (M20.3). There is no {@code off}: that word is a configuration error.
 * The only default lives here (rule C13).
 */
public enum TickLayerMode {
    /** Ready casts of M19 plus impact precision. */
    CASTS,
    /** Casts, tick signals and reaction preferences, without movement. */
    REACTIONS,
    /** Reactions plus micro-behaviours. */
    FULL;

    /** Delivery default (decision M20-D1). */
    public static final TickLayerMode DEFAULT = REACTIONS;

    /**
     * Lesser of two modes. Order is {@code CASTS < REACTIONS < FULL}.
     *
     * @param other the other mode
     * @return the mode that does less
     */
    public TickLayerMode lesser(TickLayerMode other) {
        return ordinal() <= other.ordinal() ? this : other;
    }
}
