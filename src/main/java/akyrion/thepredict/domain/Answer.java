package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to one question: a probability per tactic and an overall confidence. Immutable.
 *
 * <p>Every probability is finite and within {@code [0, 1]}, and their sum may differ from 1 by at
 * most {@value #SUM_TOLERANCE} (plus {@value #ROUNDING_EPSILON} for floating-point rounding).
 * The tolerance is a design choice that M08 confirms with real Jev answers.
 *
 * @param probabilities probability of each tactic; not empty
 * @param confidence overall confidence within {@code [0, 1]}
 */
public record Answer(Map<TacticId, Double> probabilities, double confidence) {
    /** Largest accepted difference between the sum of probabilities and 1. */
    public static final double SUM_TOLERANCE = 0.02;
    /** Allowance for floating-point rounding in comparisons. */
    public static final double ROUNDING_EPSILON = 1e-9;

    private static final Comparator<Map.Entry<TacticId, Double>> RANKING =
            Comparator.<Map.Entry<TacticId, Double>>comparingDouble(Map.Entry::getValue).reversed()
                    .thenComparing(entry -> entry.getKey().value());

    /**
     * Validates the answer and copies the probabilities.
     *
     * @throws NullPointerException if a component, key or value is {@code null}
     * @throws IllegalArgumentException if a probability or the confidence is out of range, or the
     *         sum is outside the tolerance
     */
    public Answer {
        probabilities = Map.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
        DomainChecks.requireUnit(confidence, "confidence");
        if (probabilities.isEmpty()) {
            throw new IllegalArgumentException("probabilities must not be empty");
        }
        var sum = 0.0;
        for (var probability : probabilities.values()) {
            sum += DomainChecks.requireUnit(probability, "probabilities");
        }
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE + ROUNDING_EPSILON) {
            throw new IllegalArgumentException("probabilities must sum to 1 within " + SUM_TOLERANCE + ", was " + sum);
        }
    }

    /**
     * Returns every tactic by descending probability, ties broken by ascending tactic name so the
     * order is deterministic.
     *
     * @return immutable ranked list, never empty
     */
    public List<TacticId> ranked() {
        var entries = new ArrayList<>(probabilities.entrySet());
        entries.sort(RANKING);
        var ranked = new ArrayList<TacticId>(entries.size());
        for (var entry : entries) {
            ranked.add(entry.getKey());
        }
        return List.copyOf(ranked);
    }

    /**
     * Returns the tactic with the highest probability, as ordered by {@link #ranked()}.
     *
     * @return the best tactic
     */
    public TacticId best() {
        return ranked().getFirst();
    }

    /**
     * Returns the probability of a tactic.
     *
     * @param tactic tactic name
     * @return its probability, or {@code 0} when the answer does not mention it
     */
    public double probabilityOf(TacticId tactic) {
        return probabilities.getOrDefault(Objects.requireNonNull(tactic, "tactic"), 0.0);
    }
}
