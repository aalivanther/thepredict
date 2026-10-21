package akyrion.thepredict.domain;

/**
 * Base stance chosen by the tactic (M23.1). The visible stance is the action while one is in progress.
 */
public enum BaseStance {
    /** No pressure and no guard. */
    NEUTRAL,
    /** Closing or flanking. */
    PRESSING,
    /** Holding ground. */
    GUARDED,
    /** Leaving or keeping away. */
    EVASIVE;

    /**
     * The stance of a movement when the profile does not override it.
     *
     * @param movement movement of the intent
     * @return the default base
     */
    public static BaseStance fromMovement(Movement movement) {
        return switch (movement) {
            case APPROACH, FLANK -> PRESSING;
            case HOLD -> GUARDED;
            case RETREAT -> EVASIVE;
            case KEEP_DISTANCE, NONE -> NEUTRAL;
        };
    }
}
