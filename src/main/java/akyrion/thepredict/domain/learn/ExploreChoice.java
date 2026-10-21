package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.BehaviorPolicy;

/**
 * One exploration decision (M28.5). Outside the stratum the executed propensity is 1 and the behavior is not explore.
 * The recorded vector, when exploring, is the plan mixture.
 *
 * @param index chosen candidate
 * @param behavior recorded behavior
 * @param inStratum whether Jev is suppressed
 * @param propensity propensity of the executed candidate
 * @param propensities the plan mixture, summing to 1
 */
public record ExploreChoice(int index, BehaviorPolicy behavior, boolean inStratum, double propensity,
                            double[] propensities) {
    /**
     * Copies the vector.
     */
    public ExploreChoice {
        propensities = propensities.clone();
    }
}
