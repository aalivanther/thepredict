package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.PlattMath;

import java.util.Arrays;
import java.util.Optional;

/**
 * Applies a Platt fit on the server (M28.4). The fit is built off the server. A stale model uses the raw threshold
 * and {@link LearnCause#CALIBRATION_STALE}. Fallback is the exact key, then any option band, then the identity.
 */
public final class ConfidenceCalibrator {
    private final CalibrationKey[] keys;
    private final PlattMath.Fit[] fits;
    private final String model;

    /**
     * Creates a calibrator.
     *
     * @param model model this artifact was built for
     * @param keys keys, exact first
     * @param fits fits aligned with {@code keys}
     */
    public ConfidenceCalibrator(String model, CalibrationKey[] keys, PlattMath.Fit[] fits) {
        this.model = model;
        this.keys = keys.clone();
        this.fits = fits.clone();
    }

    /**
     * The identity calibrator.
     *
     * @param model live model
     * @return a calibrator that does not move confidence
     */
    public static ConfidenceCalibrator identity(String model) {
        return new ConfidenceCalibrator(model, new CalibrationKey[0], new PlattMath.Fit[0]);
    }

    /**
     * Keeps the fit only when five-fold Brier by scenario beats the identity.
     *
     * @param model model name
     * @param key calibration key
     * @param fit candidate fit
     * @param confidence raw confidences
     * @param success 1 or 0
     * @return a calibrator whose fit is the identity when the candidate does not improve
     */
    public static ConfidenceCalibrator of(String model, CalibrationKey key, PlattMath.Fit fit, double[] confidence,
                                          int[] success) {
        var kept = PlattMath.retain(fit, confidence, success);
        return new ConfidenceCalibrator(model, new CalibrationKey[] {key}, new PlattMath.Fit[] {kept});
    }

    /**
     * Calibrated confidence. A stale model returns the raw value.
     *
     * @param key requested key
     * @param liveModel model in the current config
     * @param confidence raw confidence
     * @return the calibrated probability
     */
    public double apply(CalibrationKey key, String liveModel, double confidence) {
        if (!model.equals(liveModel)) {
            return confidence;
        }
        var fit = find(key);
        return PlattMath.apply(confidence, fit.slope(), fit.bias());
    }

    /**
     * Raw threshold for a profile floor. Parity targets {@code p* = calib(floor)}; the calibrator is increasing, so
     * the raw threshold of that target is the floor itself, returned as is and never through the clipped logit, where
     * a large bias would move it.
     *
     * @param key requested key
     * @param liveModel model in the current config
     * @param floor profile minimum confidence
     * @param parity whether the target is parity
     * @param explicit explicit probability when parity is false
     * @return the raw threshold and a cause when the identity was forced
     */
    public Result threshold(CalibrationKey key, String liveModel, double floor, boolean parity, double explicit) {
        if (!model.equals(liveModel)) {
            return new Result(floor, Optional.of(LearnCause.CALIBRATION_STALE));
        }
        var fit = find(key);
        if (fit.identity()) {
            return new Result(floor, fit.cause());
        }
        if (parity) {
            return new Result(floor, Optional.empty());
        }
        return new Result(PlattMath.rawThreshold(explicit, fit.slope(), fit.bias(), floor), Optional.empty());
    }

    private PlattMath.Fit find(CalibrationKey key) {
        for (var index = 0; index < keys.length; index++) {
            if (keys[index].equals(key)) {
                return fits[index];
            }
        }
        for (var index = 0; index < keys.length; index++) {
            if (sameIgnoringOptions(keys[index], key)) {
                return fits[index];
            }
        }
        return PlattMath.Fit.IDENTITY;
    }

    private static boolean sameIgnoringOptions(CalibrationKey left, CalibrationKey right) {
        return left.model().equals(right.model()) && left.detail() == right.detail() && left.kind() == right.kind()
                && left.target() == right.target();
    }

    /**
     * A threshold decision.
     *
     * @param rawThreshold confidence to compare
     * @param cause why the identity was used, or empty
     */
    public record Result(double rawThreshold, Optional<LearnCause> cause) {
        /**
         * Copies nothing; the record is already immutable.
         */
        public Result {
            if (Double.isNaN(rawThreshold)) {
                throw new IllegalArgumentException("threshold");
            }
        }
    }

    /**
     * Whether two calibrators have the same fits. Used by tests.
     *
     * @return the model
     */
    public String model() {
        return model;
    }

    /**
     * Fit count.
     *
     * @return how many keys
     */
    public int size() {
        return keys.length;
    }

    @Override
    public String toString() {
        return model + " " + Arrays.hashCode(fits);
    }
}
