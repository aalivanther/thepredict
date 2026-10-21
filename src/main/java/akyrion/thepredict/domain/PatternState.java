package akyrion.thepredict.domain;

/**
 * State of one pattern inside an encounter (M26.3, M26.6).
 */
public enum PatternState {
    /** Not yet shown, or left because the mean fell. */
    INACTIVE,
    /** The evidence cleared the threshold. */
    ACTIVE,
    /** Resting after motivated hits. No bias, fact or signal. */
    RESTING
}
