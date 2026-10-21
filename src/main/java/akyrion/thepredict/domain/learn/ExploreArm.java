package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.BehaviorPolicy;

import java.util.Objects;

/**
 * Exploration armed for one capture (M28.5). The idle arm is blocked, so an unarmed session does not explore.
 *
 * @param seed session seed
 * @param greedy greedy index
 * @param candidates candidate count
 * @param epsilon explore probability
 * @param explored adoptions already explored
 * @param adoptions adoptions so far
 * @param maxShare cap
 * @param blocked survival, telegraph, execution, or a started preparation
 * @param base behavior used outside the stratum
 * @param acceptedJev whether an accepted Jev answer forbids dropping the question
 */
public record ExploreArm(long seed, int greedy, int candidates, double epsilon, int explored, int adoptions,
                         double maxShare, boolean blocked, BehaviorPolicy base, boolean acceptedJev) {
    /** An arm that never enters the stratum. */
    public static final ExploreArm NONE = new ExploreArm(0L, 0, 1, 0.0, 0, 0, 1.0, true, BehaviorPolicy.JEV_ARGMAX,
            true);

    /**
     * Validates the arm.
     */
    public ExploreArm {
        Objects.requireNonNull(base, "base");
        if (candidates < 1) {
            throw new IllegalArgumentException("candidates");
        }
    }

    /**
     * The same arm after Jev's answer was accepted. Exploration must not replace it.
     *
     * @return an arm that does not suppress the question
     */
    public ExploreArm accepted() {
        return new ExploreArm(seed, greedy, candidates, epsilon, explored, adoptions, maxShare, blocked, base, true);
    }
}
