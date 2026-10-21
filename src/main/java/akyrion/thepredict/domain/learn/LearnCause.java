package akyrion.thepredict.domain.learn;

/**
 * Why a learned policy changed or abstained (M28, rule A7). Not a {@code FallbackReason}: low confidence stays there.
 */
public enum LearnCause {
    /** Learned scores changed the local order, without a pattern. */
    LEARNED_ORDER,
    /** The pattern lookup changed {@code localPick}. Consumes the exploit budget. */
    LEARNED_PATTERN_ORDER,
    /** The cell exists and its effective count is below the minimum. */
    VALUE_WEAK,
    /** The fitted slope is at most 0.05, so the calibrator stays the identity. */
    CALIBRATION_NON_MONOTONE,
    /** Jev was not asked. The adoption is a held intent. */
    VOI_SKIP,
    /** The held skip expired. */
    VOI_SKIP_EXPIRED,
    /** Rule 8 failed for a held skip. The parity exception does not apply. */
    VOI_SKIP_SITUATION_CHANGED,
    /** The model no longer matches the artifact. */
    CALIBRATION_STALE,
    /** Fewer than the minimum labelled decisions. */
    CALIBRATION_INSUFFICIENT,
    /** The artifact has no passing verdict. */
    UNVERIFIED_ARTIFACT,
    /** Verdicts are still loading. */
    PENDING_VERDICT,
    /** Drift or entropy alarm. Does not retrain. */
    DRIFT_ALARM,
    /** A player joined, so exploration stopped. */
    PLAYER_JOINED,
    /** The fine key was folded because the profile cap was full. */
    CONTEXT_KEY_FOLDED,
    /** The credit ledger dropped the oldest window. */
    CREDIT_TRUNCATED,
    /** Tactic entropy stayed high. The learn line uses {@link #DRIFT_ALARM} and this cause. */
    ENTROPY,
    /** One tactic took more than 80 percent of the greedy mass. Does not block. */
    COLLAPSE_RISK,
    /** A choice had no outcome type. */
    UNRATED,
    /** A covered cell was delegated. */
    DISTILLED_CELL,
    /** SPIBB replaced the local order. */
    DISTILLED_VALUE_OVERRIDE,
    /** Rule 8 failed for a distilled hold. The parity exception does not apply. */
    DISTILLED_SITUATION_CHANGED,
    /** The distilled hold expired. */
    DISTILLED_EXPIRED,
    /** The fitted slope is monotone, and the five-fold Brier does not beat the identity, so the identity stays. */
    CALIBRATION_NOT_BETTER
}
