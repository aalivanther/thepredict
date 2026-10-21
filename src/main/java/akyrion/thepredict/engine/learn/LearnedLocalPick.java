package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.UtilityScore;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.LearnedValueConsideration;
import akyrion.thepredict.domain.learn.ValueEstimate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Local leader under {@code candidateScoring: learned} (M28.7). An inactive table, or a spread below the minimum,
 * returns empty so the previous chain stays. The question order is not touched. The overloads that take a profile
 * id and a {@link ContextKey} read a table built from an artifact without loss; the ones that take integer codes are
 * the legacy entry points of callers that still hash the names with {@link #tacticCode(String)}.
 */
public final class LearnedLocalPick {
    private LearnedLocalPick() {
    }

    /**
     * Legacy 8-bit tactic code shared by the legacy build and the legacy lookup. Two ids can share it; the legacy
     * build leaves both out ({@code ValueHierarchy}).
     *
     * @param id tactic id text
     * @return a code in {@code 0..255}
     */
    public static int tacticCode(String id) {
        var hash = 0;
        for (var index = 0; index < id.length(); index++) {
            hash = hash * 31 + id.charAt(index);
        }
        return hash & 0xff;
    }

    /**
     * The eligible tactic with the highest learned score, when the factors are not flat.
     *
     * @param table active table
     * @param profile profile code
     * @param tactics eligible tactics
     * @param fine packed fine context
     * @param minSpreadMillis minimum value spread
     * @return the leader, or empty when learning must not reorder
     */
    public static Optional<TacticId> lead(ValueTable table, int profile, List<Tactic> tactics, long fine,
                                          int minSpreadMillis) {
        var weights = new double[tactics.size()];
        java.util.Arrays.fill(weights, 1.0);
        return lead(table, profile, tactics, fine, minSpreadMillis, CandidateScoring.PRIORITY, Set.of(), weights);
    }

    /**
     * The eligible tactic with the highest learned score. Weak cells stay in the comparison. {@code weight} moves
     * only a local pick.
     *
     * @param table active table
     * @param profile profile code
     * @param tactics eligible tactics
     * @param fine packed fine context
     * @param minSpreadMillis minimum value spread
     * @param baseScoring {@code learning.baseScoring}
     * @param now conditions that hold
     * @param weight one local-pick weight applied to every tactic
     * @return the leader, or empty when learning must not reorder
     */
    public static Optional<TacticId> lead(ValueTable table, int profile, List<Tactic> tactics, long fine,
                                          int minSpreadMillis, CandidateScoring baseScoring, Set<UseCondition> now,
                                          double weight) {
        var weights = new double[tactics.size()];
        java.util.Arrays.fill(weights, weight);
        return lead(table, profile, tactics, fine, minSpreadMillis, baseScoring, now, weights);
    }

    /**
     * The eligible tactic with the highest learned score. {@code weights} are per tactic and move only a local pick.
     *
     * @param table active table
     * @param profile profile code
     * @param tactics eligible tactics
     * @param fine packed fine context
     * @param minSpreadMillis minimum value spread
     * @param baseScoring {@code learning.baseScoring}
     * @param now conditions that hold
     * @param weights local-pick weights, all {@code 1} for the question order
     * @return the leader, or empty when learning must not reorder
     */
    public static Optional<TacticId> lead(ValueTable table, int profile, List<Tactic> tactics, long fine,
                                          int minSpreadMillis, CandidateScoring baseScoring, Set<UseCondition> now,
                                          double[] weights) {
        return leader(tactics, scoreMillis(table, profile, tactics, fine, minSpreadMillis, baseScoring, now, weights));
    }

    /**
     * The eligible tactic with the highest learned score, read by name with the live context key.
     *
     * @param table table read from an artifact
     * @param profile profile id
     * @param tactics eligible tactics
     * @param context context key of the capture
     * @param minimum usable windows
     * @param minSpreadMillis minimum value spread
     * @param baseScoring {@code learning.baseScoring}
     * @param now conditions that hold
     * @param weights local-pick weights, all {@code 1} for the question order
     * @return the leader, or empty when learning must not reorder
     */
    public static Optional<TacticId> lead(ValueTable table, String profile, List<Tactic> tactics, ContextKey context,
                                          int minimum, int minSpreadMillis, CandidateScoring baseScoring,
                                          Set<UseCondition> now, double[] weights) {
        return leader(tactics, scoreMillis(table, profile, tactics, context, minimum, minSpreadMillis, baseScoring,
                now, weights));
    }

    private static Optional<TacticId> leader(List<Tactic> tactics, int[] scores) {
        if (flatScores(scores)) {
            return Optional.empty();
        }
        var best = 0;
        for (var index = 1; index < scores.length; index++) {
            if (scores[index] > scores[best]) {
                best = index;
            }
        }
        return Optional.of(tactics.get(best).id());
    }

    /**
     * {@code round(1000 · base · factor · weight)} for every tactic, including weak cells.
     *
     * @param table active table
     * @param profile profile code
     * @param tactics eligible tactics
     * @param fine packed fine context
     * @param minSpreadMillis minimum value spread
     * @param baseScoring {@code learning.baseScoring}
     * @param now conditions that hold
     * @param weights per-tactic local weights
     * @return the scores
     */
    public static int[] scoreMillis(ValueTable table, int profile, List<Tactic> tactics, long fine,
                                    int minSpreadMillis, CandidateScoring baseScoring, Set<UseCondition> now,
                                    double[] weights) {
        var values = new int[tactics.size()];
        var usable = new boolean[tactics.size()];
        for (var index = 0; index < tactics.size(); index++) {
            var code = tacticCode(tactics.get(index).id().value());
            note(table.lookup(profile, code, fine, 0L, 20), 20, index, values, usable);
        }
        return scores(tactics, values, usable, minSpreadMillis, baseScoring, now, weights);
    }

    /**
     * {@code round(1000 · base · factor · weight)} for every tactic, read by name with the live context key. This is
     * the lossless form of {@link #scoreMillis(ValueTable, int, List, long, int, CandidateScoring, Set, double[])}:
     * the level 2 cell is reachable, and the minimum is {@code learning.minWindowsForValue}.
     *
     * @param table table read from an artifact
     * @param profile profile id
     * @param tactics eligible tactics
     * @param context context key of the capture
     * @param minimum usable windows
     * @param minSpreadMillis minimum value spread
     * @param baseScoring {@code learning.baseScoring}
     * @param now conditions that hold
     * @param weights per-tactic local weights
     * @return the scores
     */
    public static int[] scoreMillis(ValueTable table, String profile, List<Tactic> tactics, ContextKey context,
                                    int minimum, int minSpreadMillis, CandidateScoring baseScoring,
                                    Set<UseCondition> now, double[] weights) {
        var values = new int[tactics.size()];
        var usable = new boolean[tactics.size()];
        for (var index = 0; index < tactics.size(); index++) {
            var estimate = table.lookup(profile, tactics.get(index).id().value(), context);
            note(estimate, minimum, index, values, usable);
        }
        return scores(tactics, values, usable, minSpreadMillis, baseScoring, now, weights);
    }

    private static void note(ValueEstimate estimate, int minimum, int index, int[] values, boolean[] usable) {
        usable[index] = !estimate.none() && !estimate.weak(minimum);
        values[index] = estimate.valueMillis();
    }

    private static int[] scores(List<Tactic> tactics, int[] values, boolean[] usable, int minSpreadMillis,
                                CandidateScoring baseScoring, Set<UseCondition> now, double[] weights) {
        var factors = LearnedValueConsideration.factors(values, usable, minSpreadMillis);
        var scores = new int[tactics.size()];
        for (var index = 0; index < tactics.size(); index++) {
            var weight = weights.length > index ? weights[index] : 1.0;
            scores[index] = LearnedValueConsideration.scoreMillis(base(tactics.get(index), baseScoring, now) * weight,
                    factors[index]);
        }
        return scores;
    }

    private static boolean flatScores(int[] scores) {
        if (scores.length == 0) {
            return true;
        }
        var seen = scores[0];
        for (var score : scores) {
            if (score != seen) {
                return false;
            }
        }
        return true;
    }

    private static double base(Tactic tactic, CandidateScoring scoring, Set<UseCondition> now) {
        if (scoring == CandidateScoring.UTILITY) {
            return UtilityScore.priority(tactic.priority());
        }
        return Math.min(1.0, Math.max(0.0, tactic.priority() / 100.0));
    }
}
