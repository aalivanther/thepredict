package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prepared intentions of one capture (M20.6). {@code candidates.get(0)} is the local plan. {@code questionOrder} is
 * the order sent to Jev: with priority scoring and diversity off it stays the order of M19, so the question bytes do
 * not move.
 *
 * @param captureTick tick of the capture
 * @param signature situation signature
 * @param candidates one to K distinct tactics, index 0 the local plan
 * @param pruned why a tactic left
 * @param questionOrder order of the question options
 */
public record CandidateSet(long captureTick, int signature, List<CandidateIntent> candidates,
                           Map<TacticId, PruneReason> pruned, List<TacticId> questionOrder) {
    /**
     * Copies the collections and checks the invariants.
     *
     * @throws IllegalArgumentException if the list is empty, repeats a tactic, or index 0 is not the best score
     */
    public CandidateSet {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        pruned = Map.copyOf(Objects.requireNonNull(pruned, "pruned"));
        questionOrder = List.copyOf(Objects.requireNonNull(questionOrder, "questionOrder"));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        var seen = new HashSet<TacticId>();
        var best = -1;
        for (var candidate : candidates) {
            if (!seen.add(candidate.tactic())) {
                throw new IllegalArgumentException("duplicate tactic " + candidate.tactic().value());
            }
            if (candidate.scoreMillis() > best) {
                best = candidate.scoreMillis();
            }
        }
        if (candidates.getFirst().scoreMillis() != best) {
            throw new IllegalArgumentException("candidates.get(0) must be the highest score");
        }
    }

    /**
     * Builds a set, moving the highest score to index 0 without changing {@code questionOrder}. A tie keeps the earlier
     * candidate first.
     *
     * @param captureTick tick of the capture
     * @param signature situation signature
     * @param scored scored candidates in question order
     * @param pruned dropped tactics
     * @param questionOrder order sent to Jev
     * @return the set
     * @throws IllegalArgumentException if {@code scored} is empty or repeats a tactic, as the constructor
     */
    public static CandidateSet of(long captureTick, int signature, List<CandidateIntent> scored,
                                  Map<TacticId, PruneReason> pruned, List<TacticId> questionOrder) {
        if (Objects.requireNonNull(scored, "scored").isEmpty()) {
            return new CandidateSet(captureTick, signature, scored, pruned, questionOrder);
        }
        var bestIndex = 0;
        for (var index = 1; index < scored.size(); index++) {
            var better = scored.get(index).scoreMillis() > scored.get(bestIndex).scoreMillis();
            if (better) {
                bestIndex = index;
            }
        }
        var ordered = new ArrayList<CandidateIntent>(scored.size());
        ordered.add(scored.get(bestIndex));
        for (var index = 0; index < scored.size(); index++) {
            if (index != bestIndex) {
                ordered.add(scored.get(index));
            }
        }
        return new CandidateSet(captureTick, signature, ordered, pruned, questionOrder);
    }

    /**
     * Local plan under a damage penalty (M22.7, rule A10): the largest {@code scoreMillis − penalty}, where only a
     * candidate of damage {@code HIGH} or above pays the penalty. A penalty of 0 returns index 0, which is the M20
     * plan, and a tie keeps the earlier candidate. The question order is not touched.
     *
     * @param levers levers; only {@code localDamagePenaltyMillis} is read
     * @return the index in {@code candidates}
     */
    public int localPick(PressureLevers levers) {
        if (levers.localDamagePenaltyMillis() == 0) {
            return 0;
        }
        var best = 0;
        var bestScore = adjusted(candidates.getFirst(), levers);
        for (var index = 1; index < candidates.size(); index++) {
            var score = adjusted(candidates.get(index), levers);
            if (score > bestScore) {
                best = index;
                bestScore = score;
            }
        }
        return best;
    }

    private static int adjusted(CandidateIntent candidate, PressureLevers levers) {
        var penalty = candidate.damage().compareTo(DamageTier.HIGH) >= 0 ? levers.localDamagePenaltyMillis() : 0;
        return candidate.scoreMillis() - penalty;
    }
}
