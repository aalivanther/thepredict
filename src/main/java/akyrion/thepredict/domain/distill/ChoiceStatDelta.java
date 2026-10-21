package akyrion.thepredict.domain.distill;

/**
 * One upsert of {@code m29_choice_stats} (M29.1).
 *
 * @param key the row
 * @param offered how many times the tactic was offered
 * @param chosen how many times it was the label
 * @param epochMs wall time supplied by the adapter
 */
public record ChoiceStatDelta(ChoiceStatKey key, int offered, int chosen, long epochMs) {
    /**
     * Checks the counts.
     */
    public ChoiceStatDelta {
        if (key == null) {
            throw new NullPointerException("choice delta");
        }
        if (offered < 0 || chosen < 0 || chosen > offered) {
            throw new IllegalArgumentException("choice counts");
        }
    }
}
