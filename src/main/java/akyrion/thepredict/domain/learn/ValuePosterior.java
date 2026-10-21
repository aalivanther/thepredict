package akyrion.thepredict.domain.learn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posterior means of the four levels and the disagreement delta of each coarse cell (M28.3, M28.8). Level 1 shrinks
 * toward the profile mean, level 2 toward its level 1 parent, level 3 toward its level 2 parent, and level 0 toward
 * the mean of the build.
 *
 * <p>The delta of a coarse cell is {@code Δ = Σ d·(v̂(t, f) − v̂(a*, f)) / Σ d} over its fine contexts {@code f} and
 * the tactics {@code t ≠ a*}, where {@code a*} is the tactic of the highest level 2 value, {@code d} is the number of
 * windows in which Jev was asked and did not match the learned leader, and {@code v̂(a*, f)} falls back to the level 2
 * value of {@code a*} when {@code a*} has no fine cell in {@code f}. Comparing inside the same fine context lets
 * {@code Δ} be positive when Jev's other picks did better than the leader would have there. Without disagreements it
 * is 0.
 */
final class ValuePosterior {
    final Map<String, Double> fineValues = new LinkedHashMap<>();
    final Map<String, Double> coarseValues = new LinkedHashMap<>();
    final Map<String, Double> tacticValues = new LinkedHashMap<>();
    final Map<String, Double> zeroValues = new LinkedHashMap<>();
    private final Map<String, Integer> deltas = new LinkedHashMap<>();

    private ValuePosterior() {
    }

    static ValuePosterior of(List<ValueLevels.Fine> fines, ValueLevels.Pools pools, double[] kappa) {
        var posterior = new ValuePosterior();
        for (var entry : pools.tactic.entrySet()) {
            var pool = entry.getValue();
            var root = pools.roots.get(pool.profile).mean;
            posterior.tacticValues.put(entry.getKey(), ShrinkageMath.shrink(pool.windows, pool.mean, kappa[1], root));
        }
        for (var entry : pools.coarse.entrySet()) {
            var pool = entry.getValue();
            var parent = posterior.tacticValues.get(pool.profile + "|" + pool.tactic);
            posterior.coarseValues.put(entry.getKey(), ShrinkageMath.shrink(pool.windows, pool.mean, kappa[2], parent));
        }
        for (var fine : fines) {
            var parent = posterior.coarseValues.get(ValueLevels.coarseKey(fine));
            posterior.fineValues.put(ValueLevels.fineKey(fine),
                    ShrinkageMath.shrink(fine.windows(), fine.mean(), kappa[3], parent));
        }
        for (var entry : pools.zero.entrySet()) {
            var pool = entry.getValue();
            posterior.zeroValues.put(entry.getKey(),
                    ShrinkageMath.shrink(pool.windows, pool.mean, kappa[0], pools.global.mean));
        }
        posterior.deltas(fines, pools, posterior.leaders(pools));
        return posterior;
    }

    /**
     * Delta of the coarse cell of a level 2 key, in thousandths.
     *
     * @param coarseKey {@code profile|tactic|coarseCode}
     * @return the delta, 0 without disagreements
     */
    int delta(String coarseKey) {
        return deltas.getOrDefault(coarseKey, 0);
    }

    private Map<String, String> leaders(ValueLevels.Pools pools) {
        var leaders = new LinkedHashMap<String, String>();
        for (var entry : pools.coarse.entrySet()) {
            var pool = entry.getValue();
            var cell = pool.profile + "|" + pool.coarse;
            var current = leaders.get(cell);
            if (current == null) {
                leaders.put(cell, pool.tactic);
                continue;
            }
            var best = coarseValues.get(pool.profile + "|" + current + "|" + pool.coarse);
            var order = Double.compare(coarseValues.get(entry.getKey()), best);
            if (order > 0 || order == 0 && pool.tactic.compareTo(current) < 0) {
                leaders.put(cell, pool.tactic);
            }
        }
        return leaders;
    }

    private void deltas(List<ValueLevels.Fine> fines, ValueLevels.Pools pools, Map<String, String> leaders) {
        var sums = new LinkedHashMap<String, double[]>();
        for (var fine : fines) {
            var cell = fine.profile() + "|" + fine.context().coarseCode();
            var leader = leaders.get(cell);
            var disagreements = fine.seen() - fine.agree();
            if (leader == null || leader.equals(fine.tactic()) || disagreements <= 0) {
                continue;
            }
            var reference = fineValues.get(fine.profile() + "|" + leader + "|" + fine.context().packed());
            if (reference == null) {
                reference = coarseValues.get(fine.profile() + "|" + leader + "|" + fine.context().coarseCode());
            }
            var slot = sums.computeIfAbsent(cell, ignored -> new double[2]);
            slot[0] += disagreements * (fineValues.get(ValueLevels.fineKey(fine)) - reference);
            slot[1] += disagreements;
        }
        for (var entry : pools.coarse.entrySet()) {
            var pool = entry.getValue();
            var slot = sums.get(pool.profile + "|" + pool.coarse);
            deltas.put(entry.getKey(), slot == null ? 0 : ShrinkageMath.millis(slot[0] / slot[1]));
        }
    }
}
