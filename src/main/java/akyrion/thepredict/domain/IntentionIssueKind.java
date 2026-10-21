package akyrion.thepredict.domain;

/**
 * Why a primitive or a whole {@code Intention} line was not taken as written (M19.1). The kinds of a warning leave the
 * line usable; the others make the whole line invalid, because a half intention is never used (rule A6).
 */
public enum IntentionIssueKind {
    /** Warning: a primitive the vocabulary does not know and that has no family prefix; it is ignored. */
    UNKNOWN_TOKEN,
    /** Warning: a primitive listed twice; the repetition is ignored. */
    REPEATED_TOKEN,
    /** Error: the line has no role. */
    NO_ROLE,
    /** Error: the line has more than {@value IntentionVocabulary#MAX_ROLES} roles. */
    TOO_MANY_ROLES,
    /** Error: the line has no delivery. */
    NO_DELIVERY,
    /** Error: the line has more than one delivery. */
    MORE_THAN_ONE_DELIVERY,
    /** Error: the line has more than one damage tier. */
    MORE_THAN_ONE_DAMAGE,
    /** Error: the line has more than one {@code ANSWER_} primitive (M23.2). */
    MORE_THAN_ONE_ANSWER,
    /** Error: the line has more than {@value SkillPurpose#MAX_BEST_WHEN} {@code WHEN_} conditions. */
    TOO_MANY_BEST_WHEN,
    /** Error: the line has more than {@value SkillPurpose#MAX_RISKS} {@code RISK_} risks. */
    TOO_MANY_RISKS,
    /** Error: {@code IGNORES_SHIELD} together with {@code RISK_BLOCKED_BY_SHIELD}. */
    SHIELD_CONTRADICTION,
    /**
     * Error: more than {@value IntentionVocabulary#MAX_TOKENS} raw primitives or
     * {@value IntentionVocabulary#MAX_CHARS} characters.
     */
    TOO_LONG,
    /**
     * Warning: an unknown primitive with a family prefix ({@code WHEN_}, {@code RISK_}, {@code DAMAGE_} or
     * {@code ANSWER_}), almost surely from a newer vocabulary; it is ignored and the warning says to update
     * ThePredict or remove it.
     */
    FUTURE_TOKEN,
    /** Warning: a retired name taken as its substitute ({@link IntentionVocabulary#ALIASES}). */
    ALIAS_USED;

    /**
     * Tells whether an issue of this kind makes the whole line invalid.
     *
     * @return {@code false} for the four warning kinds
     */
    public boolean invalidates() {
        return this != UNKNOWN_TOKEN && this != REPEATED_TOKEN && this != FUTURE_TOKEN && this != ALIAS_USED;
    }
}
