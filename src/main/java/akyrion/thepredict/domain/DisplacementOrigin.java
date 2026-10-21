package akyrion.thepredict.domain;

/**
 * Where a displacement reading came from (M24.6).
 */
public enum DisplacementOrigin {
    /** Every damaging branch was read. */
    READ,
    /** The author declared {@code Zone.Displacement}. */
    DECLARED,
    /** A damaging branch could not be read, so the choice is treated as knockback. */
    ASSUMED
}
