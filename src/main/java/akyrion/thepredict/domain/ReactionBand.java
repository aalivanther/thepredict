package akyrion.thepredict.domain;

/** Band of a reaction (M20.4). Spacing only moves; it never takes a cast slot. */
public enum ReactionBand {
    /** Gets the mob out of immediate danger. */
    SURVIVAL,
    /** Punishes or finishes the target. */
    OPPORTUNITY,
    /** Opens space after a cast. Movement only. */
    SPACING
}
