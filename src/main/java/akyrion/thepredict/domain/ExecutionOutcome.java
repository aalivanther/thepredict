package akyrion.thepredict.domain;

/** Result of trying to cast the choice of an intent. */
public enum ExecutionOutcome {
    /** The choice was cast. */
    CAST,
    /** The target is invulnerable; the cast may be retried in a later tick. */
    DEFERRED_INVULNERABLE,
    /** The target blocks with a shield and the choice cannot ignore it. */
    SKIPPED_SHIELD,
    /** MythicMobs refused the skill. */
    REJECTED_BY_MYTHICMOBS,
    /** The choice is not ready yet; the cast may be retried in a later tick. */
    NOT_ELIGIBLE,
    /** The intent expired before the cast. */
    EXPIRED;

    /**
     * Tells whether no further cast attempt is made for the same intent.
     *
     * @return {@code false} for {@code NOT_ELIGIBLE} and {@code DEFERRED_INVULNERABLE}, {@code true} otherwise
     */
    public boolean isFinal() {
        return this != NOT_ELIGIBLE && this != DEFERRED_INVULNERABLE;
    }
}
