package akyrion.thepredict.domain;

/**
 * What the push policy does with one choice on its ready tick (M24.6). It never defers the session.
 */
public enum PushDecision {
    /** The choice may be cast. */
    ALLOW,
    /** The choice waits and opens nothing. */
    DEFER,
    /** The choice is skipped until the danger drops below lethal. */
    SKIP
}
