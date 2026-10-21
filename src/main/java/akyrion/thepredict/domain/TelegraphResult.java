package akyrion.thepredict.domain;

/**
 * What the result window saw after impact (M23.10).
 */
public enum TelegraphResult {
    /** The hit landed. */
    LANDED,
    /** The target blocked it. */
    BLOCKED,
    /** The target was not hit inside the window. */
    AVOIDED,
    /** The window closed with nothing to report. */
    NONE
}
