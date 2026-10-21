package akyrion.thepredict.domain.distill;

import java.util.List;

/**
 * An ordered list of at most eight tactics with Luce strength in thousandths (M29.2).
 *
 * @param tactics tactic ids, best first
 * @param strengthMillis strength of each tactic, same order
 * @param source why this order was kept
 */
public record TacticOrder(List<String> tactics, List<Integer> strengthMillis, DistillSource source) {
    /** Largest order. */
    public static final int MAX = 8;

    /**
     * Copies the lists and checks the cap.
     */
    public TacticOrder {
        tactics = List.copyOf(tactics);
        strengthMillis = List.copyOf(strengthMillis);
        if (tactics.size() > MAX || tactics.size() != strengthMillis.size() || source == null) {
            throw new IllegalArgumentException("tactic order");
        }
    }

    /**
     * First tactic that is among the candidates.
     *
     * @param candidates live candidates
     * @return the tactic, or empty
     */
    public java.util.Optional<String> firstAmong(List<String> candidates) {
        for (var tactic : tactics) {
            if (candidates.contains(tactic)) {
                return java.util.Optional.of(tactic);
            }
        }
        return java.util.Optional.empty();
    }
}
