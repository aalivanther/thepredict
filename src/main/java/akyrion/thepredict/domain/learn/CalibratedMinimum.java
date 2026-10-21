package akyrion.thepredict.domain.learn;

/**
 * {@code decision.minimumCalibratedConfidence} (M28.4). {@code parity} means {@code p*} is the calibrator applied
 * to the profile's raw floor, computed per profile at load (errata E28-6 applied).
 *
 * @param parity whether the floor is the parity of the raw minimum
 * @param explicit an explicit probability in {@code [0.50, 0.99]} when parity is false
 */
public record CalibratedMinimum(boolean parity, double explicit) {
    /** Delivery default. */
    public static final CalibratedMinimum PARITY = new CalibratedMinimum(true, 0.0);

    /**
     * Validates an explicit floor.
     */
    public CalibratedMinimum {
        if (!parity && (explicit < 0.50 || explicit > 0.99)) {
            throw new IllegalArgumentException("minimumCalibratedConfidence must be parity or within [0.50, 0.99]");
        }
    }
}
