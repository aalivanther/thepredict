package akyrion.thepredict.domain.distill;

import java.util.List;
import java.util.Map;

/**
 * Immutable policy of one profile and regime (M29.2). The table wins over the tree. A pick is always one of
 * the live candidates.
 *
 * @param profile profile id
 * @param regime regime key
 * @param cells fine-cell orders
 * @param tree preference tree
 */
public record DistilledPolicy(String profile, RegimeKey regime, CellOrders cells, PreferenceTree tree) {
    /**
     * A policy that delegates nothing.
     *
     * @return an empty table and a single empty leaf
     */
    public static DistilledPolicy empty() {
        var order = new TacticOrder(List.of(), List.of(), DistillSource.LOCAL_ORDER);
        return new DistilledPolicy("", RegimeKey.ALL, new CellOrders(Map.of()), PreferenceTree.leaf(order));
    }

    /**
     * A one-cell table. The tree is a single leaf with the same order.
     *
     * @param profile profile id
     * @param context fine context
     * @param tactics tactic ids, best first
     * @return the policy
     */
    public static DistilledPolicy table(String profile, String context, List<String> tactics) {
        var strength = new java.util.ArrayList<Integer>();
        for (var index = 0; index < tactics.size(); index++) {
            strength.add(1000 - index);
        }
        var order = new TacticOrder(tactics, strength, DistillSource.TABLE);
        return new DistilledPolicy(profile, RegimeKey.ALL, new CellOrders(java.util.Map.of(context, order)),
                PreferenceTree.leaf(order));
    }

    /**
     * Chooses among candidates. Outside the orders the cause is {@link OodCause#OOD_TACTIC_UNSEEN}.
     *
     * @param candidates live candidates, declaration order
     * @param context fine context
     * @param bands band values for the tree
     * @return the choice
     */
    public DistilledChoice choose(List<String> candidates, String context, int[] bands) {
        var cell = cells.get(context);
        if (cell.isPresent()) {
            return from(cell.get(), candidates, context, "cell:" + context);
        }
        var leaf = tree.leaf(bands);
        return from(leaf.order(), candidates, "leaf" + leaf.id(), tree.path(bands));
    }

    private static DistilledChoice from(TacticOrder order, List<String> candidates, String id, String path) {
        var tactic = order.firstAmong(candidates);
        if (tactic.isEmpty()) {
            return DistilledChoice.outside(OodCause.OOD_TACTIC_UNSEEN);
        }
        var rank = order.tactics().indexOf(tactic.get());
        var strength = rank < 0 ? 0 : order.strengthMillis().get(rank);
        return DistilledChoice.picked(tactic.get(), order.source(), id, Math.max(rank, 0), strength, path);
    }
}
