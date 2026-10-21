package akyrion.thepredict.domain;

/**
 * How hard a tell must be to read (M23.4). {@code NONE} and {@code LIGHT} add no minimum.
 */
public enum DangerClass {
    /** Not an attack. */
    NONE,
    /** Vanilla melee rhythm. Minimum stays 0. */
    LIGHT,
    /** A pattern the player can learn. Not a reaction promise. */
    MEDIUM,
    /** A heavy hit with a reaction window. */
    HEAVY,
    /** A severe hit. */
    SEVERE;

    /**
     * Whether the class asks for a minimum when enforcement is on.
     *
     * @return {@code true} from {@code MEDIUM} upward
     */
    public boolean enforced() {
        return ordinal() >= MEDIUM.ordinal();
    }
}
