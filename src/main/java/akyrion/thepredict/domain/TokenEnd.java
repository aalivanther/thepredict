package akyrion.thepredict.domain;

/**
 * Why a token returned to its pool (M25). There is no epoch cause: a membership change does not return a token.
 * {@link #YIELDED} stays last.
 */
public enum TokenEnd {
    /** Returned when the impact landed. */
    RELEASED,
    /** The holder did not use it before the idle timeout. */
    IDLE_TIMEOUT,
    /** Arbitration gave it to a higher score, or capacity fell. */
    STOLEN,
    /** The holder left or died. */
    HOLDER_GONE,
    /** The tell closed. */
    TELEGRAPH_CLOSED,
    /** The holder gave it back without an impact. No producer in this module. */
    YIELDED
}
