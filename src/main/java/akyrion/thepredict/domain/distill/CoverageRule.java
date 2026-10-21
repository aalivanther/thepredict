package akyrion.thepredict.domain.distill;

import java.util.List;
import java.util.Optional;

/**
 * Whether a fine cell may be delegated (M29.3). Agreement never covers or blocks a cell. The checks run in a fixed
 * order: stale index, profile fingerprint, teacher model, regime verdict, then the cell itself.
 */
public final class CoverageRule {
    private CoverageRule() {
    }

    /**
     * The first failing cause for a cell the index holds, or empty when the cell is covered.
     *
     * @param stale whether the index is older than the maximum age
     * @param profileMatches fingerprint
     * @param modelMatches teacher model
     * @param regimeActive verdict for the mode
     * @param cell the cell
     * @param candidates tactic ids offered now
     * @param minDecisions minimum n
     * @param margin non-inferiority margin
     * @return the cause
     */
    public static Optional<OodCause> cause(boolean stale, boolean profileMatches, boolean modelMatches,
                                           boolean regimeActive, CoverageCell cell, List<String> candidates,
                                           int minDecisions, double margin) {
        var index = unseenIndex(stale, profileMatches, modelMatches, regimeActive);
        if (index.isPresent()) {
            return index;
        }
        if (cell.drifted()) {
            return Optional.of(OodCause.OOD_DRIFT);
        }
        if (cell.n() < minDecisions) {
            return Optional.of(OodCause.OOD_CELL_THIN);
        }
        if (cell.lowerBound() < -margin) {
            return Optional.of(OodCause.OOD_CELL_INFERIOR);
        }
        for (var candidate : candidates) {
            if (!cell.offered().contains(candidate)) {
                return Optional.of(OodCause.OOD_TACTIC_UNSEEN);
            }
        }
        return Optional.empty();
    }

    /**
     * The cause for a cell the index does not hold. It is never covered.
     *
     * @param stale whether the index is older than the maximum age
     * @param profileMatches fingerprint
     * @param modelMatches teacher model
     * @param regimeActive verdict for the mode
     * @return the first failing cause, {@link OodCause#OOD_CELL_UNSEEN} when the index itself is fine
     */
    public static Optional<OodCause> unseen(boolean stale, boolean profileMatches, boolean modelMatches,
                                            boolean regimeActive) {
        var index = unseenIndex(stale, profileMatches, modelMatches, regimeActive);
        return index.isPresent() ? index : Optional.of(OodCause.OOD_CELL_UNSEEN);
    }

    private static Optional<OodCause> unseenIndex(boolean stale, boolean profileMatches, boolean modelMatches,
                                                  boolean regimeActive) {
        if (stale) {
            return Optional.of(OodCause.OOD_COVERAGE_STALE);
        }
        if (!profileMatches) {
            return Optional.of(OodCause.OOD_PROFILE_CHANGED);
        }
        if (!modelMatches) {
            return Optional.of(OodCause.OOD_MODEL_CHANGED);
        }
        return regimeActive ? Optional.empty() : Optional.of(OodCause.OOD_REGIME_INACTIVE);
    }
}
