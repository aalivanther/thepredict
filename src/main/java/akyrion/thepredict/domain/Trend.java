package akyrion.thepredict.domain;

/** How the distance to the target changes between captures. */
public enum Trend {
    /** The target gets closer. */
    CLOSING,
    /** The distance barely changes. */
    STEADY,
    /** The target moves away. */
    BACKING_AWAY,
    /** The target circles around the mob. */
    STRAFING
}
