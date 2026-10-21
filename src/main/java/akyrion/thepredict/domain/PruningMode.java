package akyrion.thepredict.domain;

/**
 * Whether the question pipeline drops and reorders options (M19.6). Global setting {@code decision.pruning}.
 */
public enum PruningMode {
    /** Today's order: eligible, distinct, then priority and id, then the cap. The default. */
    OFF,
    /** Safe vetoes, the merge of equal effects and an order by the local score, then the cap. */
    SAFE;

    /** Mode of a fresh installation: the only place of this default (C13). */
    public static final PruningMode DEFAULT = OFF;
}
