package akyrion.thepredict.domain.distill;

/**
 * Why a distilled mode moved down (M29.7). It never moves up by itself.
 */
public enum DemotionCause {
    /** The audit CUSUM crossed the threshold. */
    AUDIT_DRIFT,
    /** Time-to-kill or the win rate left the G4 band. */
    OUTCOME_DRIFT,
    /** Tactic entropy stayed high. */
    ENTROPY,
    /** A fairness alarm. The whole profile goes to shadow. */
    FAIRNESS
}
