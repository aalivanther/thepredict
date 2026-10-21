package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.StableRandom;

/**
 * Epsilon exploration (M28.5). Errata E28-8 applied: {@code propensities} is {@code (1-ε)·[a=greedy]+ε/K}.
 * {@code choose} samples that mixture so a frequency test can match it, and {@link #inStratum} is a separate draw
 * that decides whether Jev is asked. {@link BehaviorPolicy#EXPLORE_JEV_SAMPLE} is never returned. An accepted Jev
 * answer is never replaced; this policy only runs before the question.
 */
public final class ExplorePolicy {
    private ExplorePolicy() {
    }

    /**
     * Mixture over {@code k} candidates. Sums to 1.
     *
     * @param greedy greedy index
     * @param candidates how many candidates
     * @param epsilon explore probability
     * @return the mixture
     */
    public static double[] propensities(int greedy, int candidates, double epsilon) {
        var pi = new double[candidates];
        for (var index = 0; index < candidates; index++) {
            pi[index] = epsilon / candidates;
        }
        pi[greedy] += 1.0 - epsilon;
        return pi;
    }

    /**
     * Whether this draw suppresses the Jev question.
     *
     * @param word a {@link StableRandom} word
     * @param epsilon explore probability
     * @return {@code true} when the unit interval draw is below epsilon
     */
    public static boolean inStratum(long word, double epsilon) {
        return unit(word) < epsilon;
    }

    /**
     * Samples an index from a mixture. Used by the frequency test and by the stratum.
     *
     * @param word a {@link StableRandom} word
     * @param propensities a mixture that sums to 1
     * @return the index
     */
    public static int sample(long word, double[] propensities) {
        var cursor = unit(word);
        var cumulative = 0.0;
        for (var index = 0; index < propensities.length; index++) {
            cumulative += propensities[index];
            if (cursor < cumulative || index == propensities.length - 1) {
                return index;
            }
        }
        return propensities.length - 1;
    }

    /**
     * Decides an adoption. Above {@code maxShare} the choice is forced greedy and still recorded.
     * Never returns {@link BehaviorPolicy#EXPLORE_JEV_SAMPLE}.
     *
     * @param seed session seed
     * @param tick server tick
     * @param greedy greedy index
     * @param candidates candidate count
     * @param epsilon explore probability
     * @param explored adoptions already explored
     * @param adoptions adoptions so far
     * @param maxShare cap
     * @param blocked survival, telegraph, execution, or a started preparation
     * @param base behavior used outside the stratum
     * @return the choice
     */
    public static ExploreChoice choose(long seed, long tick, int greedy, int candidates, double epsilon,
                                       int explored, int adoptions, double maxShare, boolean blocked,
                                       BehaviorPolicy base) {
        var pi = propensities(greedy, candidates, epsilon);
        if (blocked || overShare(explored, adoptions, maxShare)) {
            return new ExploreChoice(greedy, base, false, 1.0, pi);
        }
        var stratum = inStratum(StableRandom.draw(seed, tick, StableRandom.SALT_EXPLORE), epsilon);
        if (!stratum) {
            return new ExploreChoice(greedy, base, false, 1.0, pi);
        }
        var index = sample(StableRandom.draw(seed, tick, StableRandom.SALT_EXPLORE_INDEX), pi);
        return new ExploreChoice(index, BehaviorPolicy.EXPLORE_UNIFORM, true, pi[index], pi);
    }

    private static boolean overShare(int explored, int adoptions, double maxShare) {
        return adoptions > 0 && explored / (double) adoptions >= maxShare;
    }

    private static double unit(long word) {
        return (word >>> 11) * 0x1.0p-53;
    }
}
