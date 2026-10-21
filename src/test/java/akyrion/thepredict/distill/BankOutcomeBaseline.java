package akyrion.thepredict.distill;

import akyrion.thepredict.domain.distill.ChoiceStatKey;

/**
 * Outcome labels for the bank baseline (M29.0). A Jev answer is never a label.
 */
public final class BankOutcomeBaseline {
    private BankOutcomeBaseline() {
    }

    /**
     * One simulator outcome.
     *
     * @param model {@code outcome}, {@code rollout} or {@code kev-*}
     * @param tactic the outcome tactic
     */
    public record Row(String model, String tactic) {
    }

    /**
     * The label, when the model is allowed.
     *
     * @param row the row
     * @return the tactic
     */
    public static String label(Row row) {
        if (!ChoiceStatKey.allowed(row.model())) {
            throw new IllegalArgumentException("jev label");
        }
        return row.tactic();
    }
}
