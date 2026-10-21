package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.HealthBand;

/**
 * Three health bands of a context key (M28.2). The cut is a ratio, not the profile thresholds: high at 60 percent
 * and above, low at 30 percent and below.
 */
public enum HealthBand3 {
    /** At least 60 percent. */
    HIGH,
    /** Between the two cuts. */
    MID,
    /** At most 30 percent. */
    LOW;

    /**
     * Band of a health ratio.
     *
     * @param ratio current health over maximum, in {@code [0, 1]}
     * @return the band
     */
    public static HealthBand3 of(double ratio) {
        if (ratio >= 0.60) {
            return HIGH;
        }
        return ratio <= 0.30 ? LOW : MID;
    }

    /**
     * Band of a signature health band, folded one to one. The same fold serves the mob and its target.
     *
     * @param band the band of the situation signature
     * @return {@link #LOW} for critical, {@link #MID} for wounded and {@link #HIGH} for healthy
     */
    public static HealthBand3 of(HealthBand band) {
        return switch (band) {
            case CRITICAL -> LOW;
            case WOUNDED -> MID;
            case HEALTHY -> HIGH;
        };
    }
}
