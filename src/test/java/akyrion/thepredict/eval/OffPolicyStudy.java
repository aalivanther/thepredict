package akyrion.thepredict.eval;

import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.learn.ExplorePolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E3 on 2000 odd seeds with epsilon 0.10 (M28.6). Even seeds build the value. The formulas are not retuned.
 */
public final class OffPolicyStudy {
    /** Windows used to estimate. */
    public static final int ODD_WINDOWS = 2000;
    /** Explore probability from the plan. */
    public static final double EPSILON = 0.10;
    /** First seed. Odd seeds are {@code SEED + 2k + 1}. */
    public static final long SEED = 28L;

    private OffPolicyStudy() {
    }

    /**
     * The odd-seed report. Two calls return the same numbers.
     *
     * @return the report
     */
    public static OffPolicyEstimator.Report report() {
        return estimate(false).report();
    }

    /**
     * Whether the estimated rows are the odd seeds only.
     *
     * @return {@code true} when every estimated seed is odd
     */
    public static boolean estimateUsesOnlyOddSeeds() {
        var rows = estimate(false).rows();
        if (rows.isEmpty()) {
            return false;
        }
        for (var row : rows) {
            if ((row.seed() & 1L) == 0L) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mean doubly robust estimate minus the on-policy value 0.4, over 300 replicates.
     *
     * @return the bias
     */
    public static double bias() {
        var sum = 0.0;
        for (var replicate = 0; replicate < 300; replicate++) {
            sum += misspecified(SEED + replicate * 10_000L, EPSILON, 0).dr() - 0.8;
        }
        return sum / 300.0;
    }

    /**
     * One epsilon-0.10 replicate whose value model is 0. Doubly robust stays near 0.8 and the direct method does not.
     *
     * @return the report
     */
    public static OffPolicyEstimator.Report explored() {
        return misspecified(SEED, EPSILON, 0);
    }

    /**
     * Epsilon 0 aimed at the action the behavior never takes. Support stays below 0.99.
     *
     * @return the report
     */
    public static OffPolicyEstimator.Report withoutExploration() {
        return misspecified(SEED, 0.0, 1);
    }

    /**
     * One line for the evidence file.
     *
     * @return the line
     */
    public static String line() {
        var report = report();
        var bias = bias();
        var biasWord = Math.abs(bias) <= 0.005 ? "cumple" : "no cumple";
        return "E3 seed=" + SEED + " n=" + ODD_WINDOWS + " epsilon=" + EPSILON
                + " ips=" + report.ips() + " snips=" + report.snips() + " dr=" + report.dr()
                + " dm=" + report.dm() + " support=" + report.support() + " ess=" + report.ess()
                + " maxWeight=" + report.maxWeight() + " decision=" + report.decision()
                + " bias=" + bias + " biasDecision=" + biasWord;
    }

    private static Split estimate(boolean shift) {
        return estimate(shift, SEED);
    }

    private static Split estimate(boolean shift, long seed) {
        var evenReward = new HashMap<Integer, Double>();
        var evenCount = new HashMap<Integer, Integer>();
        var planned = new ArrayList<OffPolicyEstimator.Planned>();
        var acceptance = new ArrayList<OffPolicyEstimator.Acceptance>();
        var credit = new ArrayList<OffPolicyEstimator.CreditRow>();
        var odd = 0;
        var cursor = 0;
        while (odd < ODD_WINDOWS) {
            var sample = seed + cursor;
            cursor++;
            if (shift) {
                sample += 1;
            }
            var props = ExplorePolicy.propensities(0, 2, EPSILON);
            var action = ExplorePolicy.sample(StableRandom.draw(sample, 1L, StableRandom.SALT_EXPLORE), props);
            var reward = action == 0 ? 0.4 : -0.2;
            if ((sample & 1L) == 0L) {
                evenReward.merge(action, reward, Double::sum);
                evenCount.merge(action, 1, Integer::sum);
                continue;
            }
            odd++;
            planned.add(new OffPolicyEstimator.Planned(sample, (int) (sample % 50), action, props[action]));
            acceptance.add(new OffPolicyEstimator.Acceptance(sample, (int) (sample % 50), 0));
            credit.add(new OffPolicyEstimator.CreditRow(sample, (int) (sample % 50), reward));
        }
        var qhat = new HashMap<Integer, Double>();
        for (var action : evenReward.keySet()) {
            qhat.put(action, evenReward.get(action) / evenCount.get(action));
        }
        var target = qhat.getOrDefault(0, 0.4);
        var joined = OffPolicyEstimator.join(planned, acceptance, credit, qhat, target, 0);
        var support = new double[joined.size()];
        for (var index = 0; index < joined.size(); index++) {
            support[index] = ExplorePolicy.propensities(0, 2, EPSILON)[0];
        }
        return new Split(OffPolicyEstimator.estimate(joined, support), joined);
    }

    private static OffPolicyEstimator.Report misspecified(long seed, double epsilon, int target) {
        var planned = new ArrayList<OffPolicyEstimator.Planned>();
        var acceptance = new ArrayList<OffPolicyEstimator.Acceptance>();
        var credit = new ArrayList<OffPolicyEstimator.CreditRow>();
        var odd = 0;
        var cursor = 0;
        while (odd < ODD_WINDOWS) {
            var sample = seed + cursor;
            cursor++;
            if ((sample & 1L) == 0L) {
                continue;
            }
            odd++;
            var props = ExplorePolicy.propensities(0, 2, epsilon);
            var action = ExplorePolicy.sample(StableRandom.draw(sample, 1L, StableRandom.SALT_EXPLORE), props);
            var reward = action == 0 ? 0.8 : 0.0;
            planned.add(new OffPolicyEstimator.Planned(sample, (int) (sample % 50), action, props[action]));
            acceptance.add(new OffPolicyEstimator.Acceptance(sample, (int) (sample % 50), action));
            credit.add(new OffPolicyEstimator.CreditRow(sample, (int) (sample % 50), reward));
        }
        var qhat = new HashMap<Integer, Double>();
        qhat.put(0, 0.0);
        qhat.put(1, 0.0);
        var joined = OffPolicyEstimator.join(planned, acceptance, credit, qhat, 0.0, target);
        var support = new double[joined.size()];
        var onTarget = ExplorePolicy.propensities(0, 2, epsilon)[target];
        for (var index = 0; index < joined.size(); index++) {
            support[index] = onTarget;
        }
        return OffPolicyEstimator.estimate(joined, support);
    }

    private record Split(OffPolicyEstimator.Report report, List<OffPolicyEstimator.Joined> rows) {
    }
}
