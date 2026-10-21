package akyrion.thepredict.domain;

/**
 * Why a query found nothing (M24.4). The first matching reason in this order is the one reported.
 */
public enum ZoneMiss {
    /** No reason, because the query found a cell or was deferred. */
    NONE,
    /** No candidate was walkable. */
    NO_WALKABLE,
    /** Walkable candidates were all inside the hazard margin. */
    ALL_HAZARD,
    /** The remaining candidates were all inside the edge margin. */
    ALL_EDGE,
    /** Every scored candidate was visible to the threat. */
    ALL_VISIBLE,
    /** A step along the retreat was higher than one block. */
    BLOCKED_STEP
}
