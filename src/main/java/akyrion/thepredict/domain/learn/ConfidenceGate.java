package akyrion.thepredict.domain.learn;

/** Whether acceptance uses the raw confidence or the calibrated raw threshold (M28.7). */
public enum ConfidenceGate {
    /** Compare with {@code minimumConfidence}. Delivery default. */
    RAW,
    /** Compare with {@code c*} from the calibrator. */
    CALIBRATED;

    /** Delivery default. */
    public static final ConfidenceGate DEFAULT = RAW;
}
