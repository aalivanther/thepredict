package akyrion.thepredict.domain;

/** Health band of a mob or player over health plus absorption, derived from {@link HealthThresholds}. */
public enum HealthBand {
    /** At or below the critical ratio. */
    CRITICAL,
    /** At or below the wounded ratio and above the critical one. */
    WOUNDED,
    /** Above the wounded ratio. */
    HEALTHY
}
