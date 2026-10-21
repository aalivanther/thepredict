package akyrion.thepredict.domain;

/**
 * Result of one encounter between a player and one profile (M27.1). Ordinal {@code 4} is {@link #OTHER}. The count
 * unit is the encounter, not a mob and not a clock.
 */
public enum FightOutcome {
    /** Every mob of the profile died and the damage share met the floor. */
    WIN,
    /** The player died while a mob of this profile was still engaged, or this profile landed the killing blow. */
    LOSS,
    /** Some mobs of the profile died, below the damage-share floor. */
    ASSIST,
    /** Every clock of the profile disengaged. */
    DISENGAGED,
    /** None of the other results apply. */
    OTHER;

    /**
     * Reads a stored byte.
     *
     * @param code the ordinal
     * @return the outcome
     */
    public static FightOutcome fromCode(int code) {
        var values = values();
        if (code < 0 || code >= values.length) {
            throw new IllegalArgumentException("fight outcome " + code);
        }
        return values[code];
    }
}
