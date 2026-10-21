package akyrion.thepredict.domain;

/**
 * What a push along one line does (M24.4). Ordinals are the two-bit codes of tick signals v2.
 */
public enum PushDanger {
    /** The line is walkable and not harmful. */
    NONE,
    /** The line hurts and does not kill. */
    HARM,
    /** The line is void, lava, or a fall that meets the current health. */
    LETHAL,
    /** A cell on the line is unknown and no known cell is harm or lethal. */
    UNKNOWN;

    /**
     * The worse of two readings. Lethal beats harm, harm beats unknown, unknown beats none.
     *
     * @param other the other reading
     * @return the worse reading
     */
    public PushDanger worse(PushDanger other) {
        if (this == LETHAL || other == LETHAL) {
            return LETHAL;
        }
        if (this == HARM || other == HARM) {
            return HARM;
        }
        if (this == UNKNOWN || other == UNKNOWN) {
            return UNKNOWN;
        }
        return NONE;
    }
}
