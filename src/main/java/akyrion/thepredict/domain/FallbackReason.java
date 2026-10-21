package akyrion.thepredict.domain;

/** Why a session drops to a lower control level or an answer is rejected (rule A7). */
public enum FallbackReason {
    /** The service is temporarily unavailable. */
    SERVICE_PAUSED,
    /** The service cannot be used until its configuration changes. */
    SERVICE_BLOCKED,
    /** The request budget is exhausted. */
    NO_BUDGET,
    /** Answers repeatedly fall below the minimum confidence. */
    LOW_CONFIDENCE,
    /** The answer holds no applicable tactic. */
    INVALID_RESPONSE,
    /** The intent expired without a replacement. */
    EXPIRED,
    /** The movement makes no progress. */
    NO_PROGRESS,
    /** The encounter already has as many simultaneous retreats as it allows (M10.7). */
    RETREAT_LIMIT,
    /** The session is no longer valid. */
    SESSION_INVALID,
    /** An internal error happened. */
    INTERNAL_ERROR
}
