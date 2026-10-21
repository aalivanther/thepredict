package akyrion.thepredict.domain;

/** Perceivable condition of the mob itself. */
public enum MobCue {
    /** The mob is in water. */
    IN_WATER,
    /** The mob is in lava. */
    IN_LAVA,
    /** The mob is climbing. */
    CLIMBING,
    /** The mob cannot see its target. */
    NO_LINE_OF_SIGHT
}
