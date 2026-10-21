package akyrion.thepredict.domain;

/** Why a rung changed (M22.8). */
public enum PressureCause {
    /** The fight opened on the prior. */
    START_PRIOR,
    /** The fast loop moved the rung. */
    IN_FIGHT,
    /** An operator set the rung. Not persisted. */
    DEBUG,
    /** The next spawn will move the MythicMobs level. */
    NATIVE_LEVEL,
    /** The rung was held inside the profile band. */
    CLAMP,
    /** Learning was halved after reversals. */
    DAMPED,
    /** Five fights at the minimum rung still finished before T. */
    TARGET_UNREACHABLE_TOO_STRONG,
    /** Five fights at the maximum rung still finished after T. */
    TARGET_UNREACHABLE_TOO_WEAK,
    /** The opening rung was pulled toward this player's memory of the profile. */
    START_PRIOR_SUBJECT
}
