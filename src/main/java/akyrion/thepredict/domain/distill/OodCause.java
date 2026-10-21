package akyrion.thepredict.domain.distill;

/**
 * Why a cell is outside the coverage that may be delegated (M29.3, rule A7).
 */
public enum OodCause {
    /** No row for the fine cell. */
    OOD_CELL_UNSEEN,
    /** The cell has fewer than the minimum decisions. */
    OOD_CELL_THIN,
    /** The outcome lower bound is below the margin. Agreement does not decide this. */
    OOD_CELL_INFERIOR,
    /** A candidate was never offered in the cell. */
    OOD_TACTIC_UNSEEN,
    /** The regime has no verdict for the mode. */
    OOD_REGIME_INACTIVE,
    /** The profile fingerprint does not match the artifact. */
    OOD_PROFILE_CHANGED,
    /** The teacher model changed. */
    OOD_MODEL_CHANGED,
    /** A drift alarm removed the cell. */
    OOD_DRIFT,
    /** The coverage index is older than the maximum age. */
    OOD_COVERAGE_STALE
}
