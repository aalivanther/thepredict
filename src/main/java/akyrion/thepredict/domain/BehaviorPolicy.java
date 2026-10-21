package akyrion.thepredict.domain;

/**
 * Which policy executed a tactic (M28.1). Ordinals only grow. {@code BASE} is not recorded.
 * Errata E28-8 applied: live exploration never selects {@link #EXPLORE_JEV_SAMPLE}.
 */
public enum BehaviorPolicy {
    /** Jev's highest-probability option. Propensity 1. */
    JEV_ARGMAX,
    /** The next eligible alternative. Propensity 1. */
    ALTERNATIVE,
    /** The local plan. Propensity 1. */
    LOCAL_ARGMAX,
    /** A cache hit. Propensity 1. Excluded from calibration. */
    CACHE,
    /** A value-of-information skip. Propensity 1. */
    VOI_SKIP,
    /** Uniform exploration inside the stratum. */
    EXPLORE_UNIFORM,
    /** Present for the seven-value propensity test. Never returned by {@code ExplorePolicy.choose}. */
    EXPLORE_JEV_SAMPLE,
    /** A distilled student. Propensity 1. Audit stays {@link #JEV_ARGMAX}. */
    DISTILLED;

    /**
     * Whether the executed propensity is exactly 1.
     *
     * @return {@code true} for every policy except the two explore values
     */
    public boolean deterministic() {
        return this != EXPLORE_UNIFORM && this != EXPLORE_JEV_SAMPLE;
    }
}
