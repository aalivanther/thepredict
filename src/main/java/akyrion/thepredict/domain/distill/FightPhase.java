package akyrion.thepredict.domain.distill;

/**
 * Where a fight is in time (M29.5). Opening is before {@code distill.regime.openingSeconds}.
 */
public enum FightPhase {
    /** Before the opening threshold. */
    OPENING,
    /** Between opening and the late threshold. */
    MID,
    /** At or after the reference duration, or 60 seconds when no reference exists. */
    LATE;

    /**
     * Phase from elapsed seconds.
     *
     * @param elapsedSeconds seconds since the fight opened
     * @param openingSeconds opening length
     * @param lateSeconds late threshold
     * @return the phase
     */
    public static FightPhase of(int elapsedSeconds, int openingSeconds, int lateSeconds) {
        if (elapsedSeconds < openingSeconds) {
            return OPENING;
        }
        return elapsedSeconds >= lateSeconds ? LATE : MID;
    }
}
