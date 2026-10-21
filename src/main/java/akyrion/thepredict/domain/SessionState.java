package akyrion.thepredict.domain;

/** Lifecycle state of a mob session. */
public enum SessionState {
    /** The session takes part in decisions. */
    ACTIVE,
    /** The mob is unloaded or out of combat; the session keeps its state. */
    SUSPENDED,
    /** The session ended and is never reused. */
    CLOSED
}
