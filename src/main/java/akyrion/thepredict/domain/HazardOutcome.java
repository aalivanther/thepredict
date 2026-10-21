package akyrion.thepredict.domain;

/**
 * Why a hazard was charged to a push (M24.10).
 */
public enum HazardOutcome {
    /** The record was opened before the damage landed. */
    UNATTRIBUTED,
    /** A member push inside the attribution window was followed by a hazard. */
    PUSHED_INTO_HAZARD,
    /** The victim is a member and no push was pending. */
    SELF_HAZARD
}
