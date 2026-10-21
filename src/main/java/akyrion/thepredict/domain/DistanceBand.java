package akyrion.thepredict.domain;

/** Distance band to the target, derived from the melee and ranged reach of {@link MovementSettings}. */
public enum DistanceBand {
    /** Within melee reach. */
    MELEE,
    /** Beyond melee reach and within ranged reach. */
    RANGED,
    /** Beyond ranged reach. */
    BEYOND
}
