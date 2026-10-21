package akyrion.thepredict.domain;

/** Cause of a failed remote decision. */
public enum FailureKind {
    /** The credentials were rejected. */
    AUTHENTICATION,
    /** The model is not available. */
    MODEL_UNAVAILABLE,
    /** The service rejected the request. */
    INVALID_REQUEST,
    /** The account exceeded its rate limit. */
    RATE_LIMITED,
    /** The service is overloaded. */
    OVERLOADED,
    /** The service failed internally. */
    SERVICE_ERROR,
    /** The connection failed. */
    NETWORK,
    /** No answer arrived in time. */
    TIMEOUT,
    /** The answer could not be understood. */
    INVALID_RESPONSE
}
