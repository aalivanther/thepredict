package akyrion.thepredict.domain.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four levels of M28.3 from the fused fine cells of one build. Pools merge with Chan's formula in the canonical
 * order of the input. {@code σ²_w} is the within-cell variance pooled over all fine cells. Each level has one κ,
 * {@code clamp(σ²_w / τ², 4, 100)}, where {@code τ²} is the method-of-moments variance of the child means around
 * their parent, pooled over every parent with at least two children. Means shrink top-down from the profile mean
 * (errata E28-4) through level 1 and level 2 to level 3; level 0 shrinks toward the mean of the build. The
 * deviation of every level is the posterior {@code √(σ²_w / (n + κ))}. The disagreement delta of a coarse cell
 * compares, fine context by fine context, the tactics Jev picked against the leader of the cell.
 */
final class ValueLevels {
    private ValueLevels() {
    }

    /**
     * One fused fine cell, before shrinkage.
     *
     * @param profile profile id
     * @param tactic tactic id
     * @param context fine context
     * @param windows effective windows, week weights applied
     * @param mean weighted mean reward
     * @param m2 weighted sum of squared deviations
     * @param seen windows that asked Jev
     * @param agree of those, how often Jev matched the learned leader
     */
    record Fine(String profile, String tactic, ContextKey context, double windows, double mean, double m2, int seen,
                int agree) {
    }

    /**
     * One emitted cell, before the names are interned.
     *
     * @param profile profile id, empty for level 0
     * @param tactic tactic id
     * @param level hierarchy level
     * @param context fine or coarse code, 0 for levels 1 and 0
     * @param valueMillis posterior mean
     * @param sdMillis posterior deviation
     * @param nEff effective windows, rounded
     * @param seen Jev questions
     * @param agree Jev agreements
     * @param deltaMillis disagreement delta of the coarse cell
     */
    record Cell(String profile, String tactic, int level, long context, int valueMillis, int sdMillis, int nEff,
                int seen, int agree, int deltaMillis) {
    }

    /**
     * The cells of a build and the κ of each level.
     *
     * @param cells emitted cells
     * @param kappa κ of levels 0 to 3
     */
    record Result(List<Cell> cells, double[] kappa) {
    }

    static Result of(List<Fine> fines) {
        var pools = Pools.of(fines);
        var within = within(fines);
        var kappa = new double[ValueArtifact.LEVELS];
        kappa[3] = ShrinkageMath.kappa(within, between(pools.fineByCoarse, within));
        kappa[2] = ShrinkageMath.kappa(within, between(pools.coarseByTactic, within));
        kappa[1] = ShrinkageMath.kappa(within, between(pools.tacticByProfile, within));
        kappa[0] = ShrinkageMath.kappa(within, between(Map.of("", new ArrayList<>(pools.zero.values())), within));
        var posterior = ValuePosterior.of(fines, pools, kappa);
        var cells = new ArrayList<Cell>();
        for (var fine : fines) {
            cells.add(cell(fine.profile(), fine.tactic(), 3, fine.context().packed(),
                    posterior.fineValues.get(fineKey(fine)), within, fine.windows(), kappa[3], fine.seen(),
                    fine.agree(), posterior.delta(coarseKey(fine))));
        }
        emit(cells, pools.coarse, posterior.coarseValues, within, kappa[2], 2, posterior);
        emit(cells, pools.tactic, posterior.tacticValues, within, kappa[1], 1, posterior);
        for (var entry : pools.zero.entrySet()) {
            var pool = entry.getValue();
            cells.add(cell("", entry.getKey(), 0, 0L, posterior.zeroValues.get(entry.getKey()), within, pool.windows,
                    kappa[0], 0, 0, 0));
        }
        return new Result(List.copyOf(cells), kappa);
    }

    private static void emit(List<Cell> cells, Map<String, Pool> pools, Map<String, Double> values, double within,
                             double kappa, int level, ValuePosterior posterior) {
        for (var entry : pools.entrySet()) {
            var pool = entry.getValue();
            var delta = level == 2 ? posterior.delta(entry.getKey()) : 0;
            cells.add(cell(pool.profile, pool.tactic, level, level == 2 ? pool.coarse : 0L, values.get(entry.getKey()),
                    within, pool.windows, kappa, pool.seen, pool.agree, delta));
        }
    }

    private static Cell cell(String profile, String tactic, int level, long context, double value, double within,
                             double windows, double kappa, int seen, int agree, int delta) {
        return new Cell(profile, tactic, level, context, ShrinkageMath.millis(value),
                ShrinkageMath.millis(ShrinkageMath.deviation(within, windows, kappa)), (int) Math.round(windows),
                seen, agree, delta);
    }

    private static double within(List<Fine> fines) {
        var squares = 0.0;
        var windows = 0.0;
        for (var fine : fines) {
            squares += fine.m2();
            windows += Math.max(fine.windows() - 1.0, 0.0);
        }
        return squares / Math.max(windows, 1.0);
    }

    private static double between(Map<String, List<Pool>> groups, double within) {
        var spread = 0.0;
        var sampling = 0.0;
        var children = 0;
        for (var group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            var center = 0.0;
            for (var child : group) {
                center += child.mean;
            }
            center /= group.size();
            for (var child : group) {
                spread += (child.mean - center) * (child.mean - center);
                sampling += within / Math.max(child.windows, 1.0);
            }
            children += group.size();
        }
        return children == 0 ? ShrinkageMath.TAU_FLOOR : Math.max((spread - sampling) / children,
                ShrinkageMath.TAU_FLOOR);
    }

    static String fineKey(Fine fine) {
        return fine.profile() + "|" + fine.tactic() + "|" + fine.context().packed();
    }

    static String coarseKey(Fine fine) {
        return fine.profile() + "|" + fine.tactic() + "|" + fine.context().coarseCode();
    }

    static String tacticKey(Fine fine) {
        return fine.profile() + "|" + fine.tactic();
    }

    /** A Chan-merged pool of windows. */
    static final class Pool {
        final String profile;
        final String tactic;
        final long coarse;
        double windows;
        double mean;
        double m2;
        int seen;
        int agree;

        Pool(String profile, String tactic, long coarse) {
            this.profile = profile;
            this.tactic = tactic;
            this.coarse = coarse;
        }

        void merge(double count, double childMean, double childM2, int childSeen, int childAgree) {
            seen += childSeen;
            agree += childAgree;
            if (count <= 0.0) {
                return;
            }
            var total = windows + count;
            var gap = childMean - mean;
            mean += gap * count / total;
            m2 += childM2 + gap * gap * windows * count / total;
            windows = total;
        }
    }

    /** The pools of every level, keyed in canonical insertion order. */
    static final class Pools {
        final LinkedHashMap<String, Pool> coarse = new LinkedHashMap<>();
        final LinkedHashMap<String, Pool> tactic = new LinkedHashMap<>();
        final LinkedHashMap<String, Pool> zero = new LinkedHashMap<>();
        final LinkedHashMap<String, Pool> roots = new LinkedHashMap<>();
        final Pool global = new Pool("", "", 0L);
        final LinkedHashMap<String, List<Pool>> fineByCoarse = new LinkedHashMap<>();
        final LinkedHashMap<String, List<Pool>> coarseByTactic = new LinkedHashMap<>();
        final LinkedHashMap<String, List<Pool>> tacticByProfile = new LinkedHashMap<>();

        static Pools of(List<Fine> fines) {
            var pools = new Pools();
            for (var fine : fines) {
                var single = new Pool(fine.profile(), fine.tactic(), fine.context().coarseCode());
                single.merge(fine.windows(), fine.mean(), fine.m2(), fine.seen(), fine.agree());
                pools.fineByCoarse.computeIfAbsent(coarseKey(fine), key -> new ArrayList<>()).add(single);
                pools.add(pools.coarse, coarseKey(fine), fine, fine.context().coarseCode(), pools.coarseByTactic,
                        tacticKey(fine));
                pools.add(pools.tactic, tacticKey(fine), fine, 0L, pools.tacticByProfile, fine.profile());
                pools.zero.computeIfAbsent(fine.tactic(), key -> new Pool("", fine.tactic(), 0L))
                        .merge(fine.windows(), fine.mean(), fine.m2(), 0, 0);
                pools.roots.computeIfAbsent(fine.profile(), key -> new Pool(fine.profile(), "", 0L))
                        .merge(fine.windows(), fine.mean(), fine.m2(), 0, 0);
                pools.global.merge(fine.windows(), fine.mean(), fine.m2(), 0, 0);
            }
            return pools;
        }

        private void add(LinkedHashMap<String, Pool> level, String key, Fine fine, long coarseCode,
                         LinkedHashMap<String, List<Pool>> byParent, String parent) {
            var pool = level.get(key);
            if (pool == null) {
                pool = new Pool(fine.profile(), fine.tactic(), coarseCode);
                level.put(key, pool);
                byParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(pool);
            }
            pool.merge(fine.windows(), fine.mean(), fine.m2(), fine.seen(), fine.agree());
        }
    }
}
