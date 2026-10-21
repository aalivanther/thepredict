package akyrion.thepredict.domain.distill;

import java.util.List;
import java.util.Optional;

/**
 * Outcome label of one closed window (M29.2, errata E29-4 and E29-5 applied). The label is the candidate with
 * the highest {@code v̂ − 1.645 · sd} at a usable level. A Jev answer is never a label. No usable candidate
 * means no label.
 */
public final class LabelRule {
    /** One-sided z of the label, not {@code distill.overrideZ}. */
    public static final double Z = 1.645;
    /** Minimum effective count. */
    public static final int MIN_EFFECTIVE = 20;

    private LabelRule() {
    }

    /**
     * One candidate's estimate.
     *
     * @param tactic tactic id
     * @param mean value mean
     * @param sd standard deviation
     * @param effective effective count
     */
    public record Estimate(String tactic, double mean, double sd, int effective) {
    }

    /**
     * The label, if any candidate is usable.
     *
     * @param estimates estimates of the offered candidates
     * @return the tactic
     */
    public static Optional<String> label(List<Estimate> estimates) {
        String best = null;
        var bound = Double.NEGATIVE_INFINITY;
        for (var estimate : estimates) {
            if (estimate.effective() < MIN_EFFECTIVE) {
                continue;
            }
            var lower = estimate.mean() - Z * estimate.sd();
            if (best == null || lower > bound || (lower == bound && estimate.tactic().compareTo(best) < 0)) {
                best = estimate.tactic();
                bound = lower;
            }
        }
        return Optional.ofNullable(best);
    }
}
