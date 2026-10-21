package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * One calibration bin to add (M22.10). Sums merge; the later epoch wins the timestamp.
 *
 * @param model model name
 * @param questionDetail question detail
 * @param target calibration target, {@code effect_positive}
 * @param bin bin 0 to 19
 * @param n samples
 * @param confidenceSum sum of confidences
 * @param successSum sum of successes
 * @param lastEpochMs epoch of the latest sample
 */
public record CalibrationBinDelta(String model, String questionDetail, String target, int bin, long n,
                                  double confidenceSum, double successSum, long lastEpochMs) {
    /**
     * Checks the bin.
     *
     * @throws IllegalArgumentException if the bin or the count is out of range
     */
    public CalibrationBinDelta {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(questionDetail, "questionDetail");
        Objects.requireNonNull(target, "target");
        if (bin < 0 || bin > 19 || n < 0) {
            throw new IllegalArgumentException("calibration bin is out of range");
        }
    }
}
