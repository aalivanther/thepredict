package akyrion.thepredict.domain;

/**
 * Health ratios, over health plus absorption, that separate the {@link HealthBand} values of the
 * mob and of the players. Invariant: {@code 0 < criticalRatio < woundedRatio < 1}. Immutable.
 *
 * @param criticalRatio at or below this ratio the band is {@code CRITICAL}
 * @param woundedRatio at or below this ratio the band is {@code WOUNDED}
 */
public record HealthThresholds(double criticalRatio, double woundedRatio) {
    /**
     * Validates the thresholds.
     *
     * @throws IllegalArgumentException if the invariant does not hold
     */
    public HealthThresholds {
        if (!(criticalRatio > 0.0 && criticalRatio < woundedRatio && woundedRatio < 1.0)) {
            throw new IllegalArgumentException("health thresholds must satisfy 0 < criticalRatio < woundedRatio < 1, was "
                    + criticalRatio + " and " + woundedRatio);
        }
    }
}
