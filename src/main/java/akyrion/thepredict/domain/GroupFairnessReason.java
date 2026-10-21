package akyrion.thepredict.domain;

/**
 * Why a group cast waited (M25). {@link #CAPACITY_LOWERED} is the cause stored with {@link TokenEnd#STOLEN}
 * when a rung drops the melee cap (errata E25-6).
 */
public enum GroupFairnessReason {
    /** The weight cap of the pool is full. */
    TOKEN_CAPACITY,
    /** The forecast impact sits inside the gap of another reservation. */
    IMPACT_GAP,
    /** The player is stunned by a control of ThePredict. */
    GRACE,
    /** A heavy or severe tell is already open. */
    BIG_TELL_OPEN,
    /** An open medium tell asks for a different answer. */
    ANSWER_CONFLICT,
    /** Two medium tells are already open. */
    MEDIUM_CAP,
    /** A medium or harder tell sits inside the big-impact spacing. */
    BIG_IMPACT_SPACING,
    /** The leader holds the reserve. */
    LEADER_RESERVE,
    /** Second-hand sight cannot open a medium or harder tell. */
    SECOND_HAND,
    /** Every ring entry is still inside the retention window. */
    RESERVATION_RING_FULL,
    /** Unlocked holders were removed because the rung lowered the cap. */
    CAPACITY_LOWERED
}
