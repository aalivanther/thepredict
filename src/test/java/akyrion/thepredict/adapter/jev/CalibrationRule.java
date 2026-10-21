package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.TacticId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The rule of M16.7 that chooses {@code minimumConfidence} from the re-bench: each distinct scenario counts once, its
 * confidence is the mean of its repetitions and its tactic the mode of their best tactics (a tie goes to the higher mean
 * probability). For each threshold of {@link #THRESHOLDS}, among the reviewed and informative scenarios: {@code n} is
 * how many are kept, {@code acceptable} the share whose modal tactic is acceptable with its Wilson interval at 95 %, and
 * the forbidden count. The chosen threshold is the lowest one with {@code n >= }{@value #MIN_SCENARIOS},
 * {@code acceptable >= }{@value #MIN_ACCEPTABLE}, a Wilson lower bound {@code >= }{@value #MIN_WILSON_LOWER} and at most
 * one forbidden scenario (2 % of {@code n} once {@code n >= }{@value #LARGE_SAMPLE}); without one, 0.5 stays and M16 is
 * not closed. Pure; a test tool, never in the plugin.
 */
final class CalibrationRule {
    /** Thresholds of the curve: 0.20 to 0.70 in steps of 0.05. */
    static final double[] THRESHOLDS = {0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70};
    /** Fewest kept scenarios. */
    static final int MIN_SCENARIOS = 30;
    /** Lowest share of acceptable kept scenarios. */
    static final double MIN_ACCEPTABLE = 0.90;
    /** Lowest Wilson lower bound of that share. */
    static final double MIN_WILSON_LOWER = 0.75;
    /** Kept scenarios from which the forbidden limit is 2 % instead of one. */
    static final int LARGE_SAMPLE = 50;
    /** Normal quantile of a two-sided 95 % interval. */
    static final double Z = 1.96;

    private CalibrationRule() {
    }

    /**
     * One repetition of a scenario.
     *
     * @param best its best tactic
     * @param confidence its confidence
     * @param probabilities its probabilities
     */
    record Repetition(TacticId best, double confidence, Map<TacticId, Double> probabilities) {
        Repetition {
            Objects.requireNonNull(best, "best");
            probabilities = Map.copyOf(probabilities);
        }
    }

    /**
     * One distinct scenario of the chosen arm with its label.
     *
     * @param id scenario identifier
     * @param reviewed whether its label is {@code REVIEWED}
     * @param uninformative whether its label says nothing about accuracy
     * @param acceptable acceptable tactics
     * @param forbidden forbidden tactics
     * @param repetitions its answered repetitions, at least one
     */
    record Scenario(String id, boolean reviewed, boolean uninformative, Set<TacticId> acceptable,
                    Set<TacticId> forbidden, List<Repetition> repetitions) {
        Scenario {
            acceptable = Set.copyOf(acceptable);
            forbidden = Set.copyOf(forbidden);
            repetitions = List.copyOf(repetitions);
            if (repetitions.isEmpty()) {
                throw new IllegalArgumentException("scenario " + id + " has no repetition");
            }
        }
    }

    /**
     * A scenario reduced to one answer.
     *
     * @param id scenario identifier
     * @param modal mode of the best tactics
     * @param meanConfidence mean confidence
     * @param changes repetitions whose best tactic differs from the mode
     * @param acceptable whether the modal tactic is acceptable
     * @param forbidden whether the modal tactic is forbidden
     */
    record Aggregate(String id, TacticId modal, double meanConfidence, int changes, boolean acceptable,
                     boolean forbidden) {
    }

    /**
     * One threshold of the curve.
     *
     * @param threshold the threshold
     * @param kept scenarios kept, {@code n(t)}
     * @param keptShare share of the eligible scenarios kept
     * @param acceptable kept scenarios whose modal tactic is acceptable
     * @param acceptableShare their share among the kept, {@code 0} when none is kept
     * @param wilsonLower lower bound of its Wilson interval at 95 %
     * @param forbidden kept scenarios whose modal tactic is forbidden
     * @param meets whether the threshold meets the four conditions
     */
    record Row(double threshold, int kept, double keptShare, int acceptable, double acceptableShare,
               double wilsonLower, int forbidden, boolean meets) {
    }

    /**
     * The curve and the chosen threshold.
     *
     * @param eligible reviewed and informative scenarios
     * @param rows one row per threshold
     * @param chosen the lowest threshold that meets the rule, empty when none does
     */
    record Result(int eligible, List<Row> rows, OptionalDouble chosen) {
        Result {
            rows = List.copyOf(rows);
        }
    }

    /** Reduces the repetitions of a scenario: mode of the best tactics, tie by higher mean probability. */
    static Aggregate aggregate(Scenario scenario) {
        var counts = new HashMap<TacticId, Integer>();
        var probability = new HashMap<TacticId, Double>();
        var confidence = 0.0;
        for (var repetition : scenario.repetitions()) {
            counts.merge(repetition.best(), 1, Integer::sum);
            repetition.probabilities().forEach((tactic, value) -> probability.merge(tactic, value, Double::sum));
            confidence += repetition.confidence();
        }
        var modal = counts.keySet().stream().max(Comparator.<TacticId>comparingInt(counts::get)
                .thenComparingDouble(tactic -> probability.getOrDefault(tactic, 0.0))
                .thenComparing(TacticId::value, Comparator.reverseOrder())).orElseThrow();
        var size = scenario.repetitions().size();
        return new Aggregate(scenario.id(), modal, confidence / size, size - counts.get(modal),
                scenario.acceptable().contains(modal), scenario.forbidden().contains(modal));
    }

    /** Applies the rule to the scenarios of the chosen arm; only reviewed and informative scenarios count. */
    static Result apply(List<Scenario> scenarios) {
        var eligible = new ArrayList<Aggregate>();
        for (var scenario : scenarios) {
            if (scenario.reviewed() && !scenario.uninformative()) {
                eligible.add(aggregate(scenario));
            }
        }
        var rows = new ArrayList<Row>();
        var chosen = OptionalDouble.empty();
        for (var threshold : THRESHOLDS) {
            var kept = eligible.stream().filter(aggregate -> aggregate.meanConfidence() >= threshold
                    - 1e-12).toList();
            var n = kept.size();
            var acceptable = (int) kept.stream().filter(Aggregate::acceptable).count();
            var forbidden = (int) kept.stream().filter(Aggregate::forbidden).count();
            var share = n == 0 ? 0.0 : (double) acceptable / n;
            var lower = wilsonLower(acceptable, n);
            var meets = n >= MIN_SCENARIOS && share >= MIN_ACCEPTABLE && lower >= MIN_WILSON_LOWER
                    && forbidden <= allowedForbidden(n);
            rows.add(new Row(threshold, n, eligible.isEmpty() ? 0.0 : (double) n / eligible.size(), acceptable, share,
                    lower, forbidden, meets));
            if (meets && chosen.isEmpty()) {
                chosen = OptionalDouble.of(threshold);
            }
        }
        return new Result(eligible.size(), rows, chosen);
    }

    /** Most forbidden kept scenarios: one, or 2 % of them from {@value #LARGE_SAMPLE} on. */
    static int allowedForbidden(int kept) {
        return kept >= LARGE_SAMPLE ? (int) Math.floor(0.02 * kept) : 1;
    }

    /** Lower bound of the Wilson score interval at 95 % of {@code successes} out of {@code n}; {@code 0} for none. */
    static double wilsonLower(int successes, int n) {
        if (n == 0) {
            return 0.0;
        }
        var p = (double) successes / n;
        var z2 = Z * Z;
        var centre = p + z2 / (2.0 * n);
        var margin = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n));
        return Math.max(0.0, (centre - margin) / (1 + z2 / n));
    }
}
