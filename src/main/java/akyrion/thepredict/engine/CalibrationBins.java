package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;

/**
 * Twenty confidence bins (M22.6). Only {@link ControlLevel#JEV} windows are counted. The server thread owns the bins.
 */
public final class CalibrationBins {
    /** Bins. */
    public static final int COUNT = 20;

    private CalibrationBins() {
    }

    /**
     * Bin of a confidence in {@code [0, 1]}.
     *
     * @param confidence the confidence
     * @return {@code floor(c × 20)}, clipped to 0–19
     */
    public static int bin(double confidence) {
        var index = (int) Math.floor(confidence * COUNT);
        if (index < 0) {
            return 0;
        }
        return Math.min(COUNT - 1, index);
    }

    /**
     * Whether a closed window counts. Local and alternative control do not.
     *
     * @param level the control level
     * @return whether the bin grows
     */
    public static boolean counts(ControlLevel level) {
        return level == ControlLevel.JEV;
    }
}
