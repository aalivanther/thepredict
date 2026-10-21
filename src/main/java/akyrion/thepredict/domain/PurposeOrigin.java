package akyrion.thepredict.domain;

/**
 * Where a {@link SkillPurpose} comes from (M15.1, M19.2). Precedence, by whole purpose: {@link #DECLARED} over
 * {@link #SKILL}; a skill choice with neither has no purpose at all (it is untyped, {@link SkillChoice#isTyped()}).
 * Only a purpose written by the author ({@code DECLARED} or {@code SKILL}) changes the lint, the eligibility, the
 * pre-cast checks and the local tie-break; the fixed purpose of a vanilla melee choice behaves exactly as the 0.0.1 did.
 */
public enum PurposeOrigin {
    /**
     * Declared in {@code Choices.<id>.Purpose} of the profile: internal data and an override of the administrator,
     * never documented for authors (user decision 7 of M19).
     */
    DECLARED,
    /**
     * The fixed purpose of a vanilla melee choice, taken from its kind: a damaging melee hit. Since M19 nothing is
     * inferred from {@code PredictValues} any more (user decision 5); a skill choice never has this origin.
     */
    INFERRED,
    /** Read from the {@code Intention} line of the MythicMobs skill and completed with its native metadata (M19). */
    SKILL
}
