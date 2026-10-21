package akyrion.thepredict.domain;

/**
 * Job of one member inside a plan (M25). {@link #RESERVE} is the column that always exists.
 */
public enum GroupRole {
    /** Closes to melee. */
    PRESSURE,
    /** Takes a slot outside the shield. */
    FLANK,
    /** Stays in the distance band. */
    RANGED,
    /** Prefers a skill that can help an ally. */
    SUPPORT,
    /** Prefers control while another member holds melee. */
    CONTROL,
    /** Stands between the leader and the highest threat. */
    GUARD,
    /** Holds no job. Cost 900 when the distance is zero. */
    RESERVE
}
