package akyrion.thepredict.domain;

/**
 * Why a zone build was started (M24.2).
 */
public enum ZoneBuildReason {
    /** The encounter opened. */
    START,
    /** A periodic verification. */
    VERIFY,
    /** The anchor moved. */
    REANCHOR,
    /** The dirty ring overflowed or passed the rebuild threshold. */
    DIRTY_OVERFLOW,
    /** The world border moved. */
    BORDER
}
