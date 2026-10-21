package akyrion.thepredict.domain;

/**
 * When a choice without purpose (no {@code Purpose} in the profile and no valid {@code Intention} in the skill, M19.2)
 * may fire. Global setting {@code decision.untypedCast}.
 */
public enum UntypedCastMode {
    /** Only as the choice of its own tactic, never alone through {@code ANY_READY}. The default (user decision). */
    TACTIC_ONLY,
    /** Also as a candidate of {@code ANY_READY}, on the last step of the order and without a reach check. */
    ANY_READY;

    /** Mode of a fresh installation: the only place of this default (C13; user decision of 2026-10-01). */
    public static final UntypedCastMode DEFAULT = TACTIC_ONLY;
}
