package akyrion.thepredict.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IPS, SNIPS and doubly robust estimates from planned, acceptance and credit rows (M28.6). Test code only.
 */
public final class OffPolicyEstimator {
    private OffPolicyEstimator() {
    }

    /** A planned action and the behavior propensity of that action. */
    public record Planned(long seed, int encounter, int action, double propensity) {
    }

    /** The action that was accepted. */
    public record Acceptance(long seed, int encounter, int action) {
    }

    /** The reward of the window. */
    public record CreditRow(long seed, int encounter, double reward) {
    }

    /** One joined tuple. {@code qhat} is the value of the behavior action from the other split. */
    public record Joined(long seed, int encounter, int action, double propensity, int target, double reward,
                         double qhat, double targetValue) {
    }

    /**
     * The published estimates.
     *
     * @param ips mean of weighted rewards
     * @param snips self-normalised weighted mean
     * @param dr doubly robust estimate
     * @param dm direct method
     * @param support fraction with positive behavior propensity on the target
     * @param ess Kish effective sample size
     * @param maxWeight largest weight
     * @param decision {@code cumple} or {@code INCONCLUSIVE}
     */
    public record Report(double ips, double snips, double dr, double dm, double support, double ess, double maxWeight,
                         String decision) {
    }

    /**
     * Joins the three logs on seed and encounter. A row missing any part is dropped.
     *
     * @param planned planned lines
     * @param acceptance acceptance lines
     * @param credit credit lines
     * @param qhat value of a behavior action, from the other split
     * @param targetValue value of the target action
     * @param target the deterministic target action
     * @return the tuples
     */
    public static List<Joined> join(List<Planned> planned, List<Acceptance> acceptance, List<CreditRow> credit,
                                    Map<Integer, Double> qhat, double targetValue, int target) {
        var accepted = new HashMap<String, Acceptance>();
        for (var row : acceptance) {
            accepted.put(key(row.seed(), row.encounter()), row);
        }
        var rewards = new HashMap<String, CreditRow>();
        for (var row : credit) {
            rewards.put(key(row.seed(), row.encounter()), row);
        }
        var joined = new ArrayList<Joined>();
        for (var row : planned) {
            var id = key(row.seed(), row.encounter());
            var took = accepted.get(id);
            var scored = rewards.get(id);
            if (took == null || scored == null) {
                continue;
            }
            joined.add(new Joined(row.seed(), row.encounter(), row.action(), row.propensity(), target, scored.reward(),
                    qhat.getOrDefault(row.action(), 0.0), targetValue));
        }
        return joined;
    }

    /**
     * Estimates the target policy. Support below 0.99 or ESS below 100 is {@code INCONCLUSIVE}.
     *
     * @param rows joined odd-seed tuples
     * @param behaviorOnTarget behavior propensity of the target action, per row
     * @return the report
     */
    public static Report estimate(List<Joined> rows, double[] behaviorOnTarget) {
        var count = rows.size();
        var weighted = 0.0;
        var weightSum = 0.0;
        var weightSquare = 0.0;
        var direct = 0.0;
        var correction = 0.0;
        var supported = 0;
        var maxWeight = 0.0;
        for (var index = 0; index < count; index++) {
            var row = rows.get(index);
            var match = row.action() == row.target() ? 1.0 : 0.0;
            var weight = row.propensity() > 0.0 ? match / row.propensity() : 0.0;
            weighted += weight * row.reward();
            weightSum += weight;
            weightSquare += weight * weight;
            direct += row.targetValue();
            correction += weight * (row.reward() - row.qhat());
            if (behaviorOnTarget[index] > 0.0) {
                supported++;
            }
            if (weight > maxWeight) {
                maxWeight = weight;
            }
        }
        var ips = count == 0 ? 0.0 : weighted / count;
        var snips = weightSum == 0.0 ? 0.0 : weighted / weightSum;
        var dm = count == 0 ? 0.0 : direct / count;
        var dr = dm + (count == 0 ? 0.0 : correction / count);
        var support = count == 0 ? 0.0 : supported / (double) count;
        var ess = weightSquare == 0.0 ? 0.0 : weightSum * weightSum / weightSquare;
        var decision = support < 0.99 || ess < 100.0 ? "INCONCLUSIVE" : "cumple";
        return new Report(ips, snips, dr, dm, support, ess, maxWeight, decision);
    }

    /**
     * Cluster interval minus the naive interval, on one fixed sample. Positive means the cluster interval is wider.
     *
     * @param rewards rewards grouped so each encounter is constant
     * @param encounters encounter id of each row
     * @return cluster width minus naive width
     */
    public static double clusterGap(double[] rewards, int[] encounters) {
        return width(rewards, encounters, true) - width(rewards, encounters, false);
    }

    private static double width(double[] rewards, int[] encounters, boolean cluster) {
        var low = resample(rewards, encounters, cluster, true);
        var high = resample(rewards, encounters, cluster, false);
        return high - low;
    }

    private static double resample(double[] rewards, int[] encounters, boolean cluster, boolean low) {
        var groups = new HashMap<Integer, List<Integer>>();
        for (var index = 0; index < rewards.length; index++) {
            var id = cluster ? encounters[index] : index;
            groups.computeIfAbsent(id, key -> new ArrayList<>()).add(index);
        }
        var ids = new ArrayList<>(groups.keySet());
        var means = new double[2000];
        var state = 28L;
        for (var draw = 0; draw < means.length; draw++) {
            var sum = 0.0;
            var count = 0;
            for (var pick = 0; pick < ids.size(); pick++) {
                state = state * 6364136223846793005L + 1;
                var chosen = groups.get(ids.get((int) Long.remainderUnsigned(state, ids.size())));
                for (var index : chosen) {
                    sum += rewards[index];
                    count++;
                }
            }
            means[draw] = count == 0 ? 0.0 : sum / count;
        }
        java.util.Arrays.sort(means);
        return low ? means[50] : means[1949];
    }

    private static String key(long seed, int encounter) {
        return seed + ":" + encounter;
    }
}
