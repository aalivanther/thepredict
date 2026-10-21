package akyrion.thepredict.domain;

/**
 * Confidence of a group from the life-times-damage ratio (M25).
 */
public enum GroupConfidence {
    /** Ratio below 0.5. */
    PANICKED,
    /** Ratio below 0.8. */
    WORRIED,
    /** Ratio below 1.25. */
    NEUTRAL,
    /** Ratio below 2. */
    CONFIDENT,
    /** Ratio at least 2. */
    HEROIC
}
