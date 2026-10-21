package akyrion.thepredict.domain;

/**
 * State of one Beta pattern (M26.3). Inactive becomes active after {@code minTrials} and {@code persistUpdates}
 * consecutive tails. Active becomes inactive when the mean falls, or at the end of the encounter. A CUSUM alarm
 * halves the evidence and leaves the state. An update whose tail test fails resets the run of tails. With a seed,
 * activation also needs the own trials of this fight that {@code memory.minFightTrials} sets (A15); a caller that
 * passes none gets the default of {@link MemorySettings#DEFAULT}, so no floor is fixed here.
 */
public final class PatternTracker {
    private final EnemyPattern pattern;
    private final BetaEvidence evidence = new BetaEvidence();
    private final Cusum cusum = new Cusum();
    private PatternState state = PatternState.INACTIVE;
    private int streak;

    /**
     * Tracks one pattern.
     *
     * @param pattern a Beta pattern
     */
    public PatternTracker(EnemyPattern pattern) {
        if (!pattern.usesBeta()) {
            throw new IllegalArgumentException("fixed cadence is not a beta tracker");
        }
        this.pattern = pattern;
    }

    /**
     * The pattern.
     *
     * @return the pattern
     */
    public EnemyPattern pattern() {
        return pattern;
    }

    /**
     * The state.
     *
     * @return inactive, active or resting
     */
    public PatternState state() {
        return state;
    }

    /**
     * Consecutive updates whose posterior tail test passed: the persistence run that {@code persistUpdates} counts.
     * It is not a count of motivated hits.
     *
     * @return the run
     */
    public int streak() {
        return streak;
    }

    /**
     * The evidence.
     *
     * @return the counts
     */
    public BetaEvidence evidence() {
        return evidence;
    }

    /**
     * One Bernoulli update.
     *
     * @param success whether the occasion showed the pattern
     * @param lambda evidence forgetting
     * @param quantile tail probability
     * @param minTrials minimum trials before activation
     * @param persistUpdates consecutive tails required
     * @param exitMargin how far below theta the mean must fall
     * @param changepointH CUSUM threshold
     * @return why the record changed, or empty when nothing notable happened
     */
    public java.util.Optional<PatternCause> observe(boolean success, double lambda, double quantile, int minTrials,
                                                    int persistUpdates, double exitMargin, double changepointH) {
        return observe(success, lambda, quantile, minTrials, persistUpdates, exitMargin, changepointH, 0, false);
    }

    /**
     * One Bernoulli update that may count a seed toward {@code n} and still requires own trials (A15).
     *
     * @param success whether the occasion showed the pattern
     * @param lambda evidence forgetting
     * @param quantile tail probability
     * @param minTrials minimum detector trials before activation
     * @param persistUpdates consecutive tails required
     * @param exitMargin how far below theta the mean must fall
     * @param changepointH CUSUM threshold
     * @param minOwnTrials own trials required before activation, the configured {@code memory.minFightTrials}; 0
     *        means not configured, and a seeded pattern then needs the default of {@link MemorySettings#DEFAULT}
     * @return why the record changed, or empty when nothing notable happened
     */
    public java.util.Optional<PatternCause> observe(boolean success, double lambda, double quantile, int minTrials,
                                                    int persistUpdates, double exitMargin, double changepointH,
                                                    int minOwnTrials) {
        return observe(success, lambda, quantile, minTrials, persistUpdates, exitMargin, changepointH, minOwnTrials,
                true);
    }

    private java.util.Optional<PatternCause> observe(boolean success, double lambda, double quantile, int minTrials,
                                                     int persistUpdates, double exitMargin, double changepointH,
                                                     int minOwnTrials, boolean countSeed) {
        evidence.update(success, lambda);
        var tail = BetaMath.tailBelow(pattern.theta(), quantile, evidence.alpha(), evidence.beta());
        streak = tail ? streak + 1 : 0;
        if (state == PatternState.ACTIVE || state == PatternState.RESTING) {
            return whileUp(success, exitMargin, changepointH);
        }
        var seen = countSeed ? evidence.detectorTrials() : evidence.trials();
        if (seen >= minTrials && evidence.trials() >= ownTrials(minOwnTrials) && streak >= persistUpdates) {
            state = PatternState.ACTIVE;
            cusum.arm(evidence.mean());
            return java.util.Optional.of(PatternCause.ENTER);
        }
        return java.util.Optional.empty();
    }

    private int ownTrials(int configured) {
        if (configured > 0 || evidence.seededTrials() == 0) {
            return configured;
        }
        return MemorySettings.DEFAULT.minFightTrials();
    }

    /**
     * Opens a rest. Facts and signals leave immediately.
     */
    public void rest() {
        state = PatternState.RESTING;
    }

    /**
     * Closes a rest. The pattern stays active only while the mean is still above theta.
     *
     * @return the cause
     */
    public PatternCause endRest() {
        state = evidence.mean() > pattern.theta() ? PatternState.ACTIVE : PatternState.INACTIVE;
        return PatternCause.REST_END;
    }

    /**
     * Closes the encounter.
     *
     * @return the cause
     */
    public PatternCause endEncounter() {
        state = PatternState.INACTIVE;
        cusum.reset();
        return PatternCause.ENCOUNTER_END;
    }

    private java.util.Optional<PatternCause> whileUp(boolean success, double exitMargin, double changepointH) {
        if (evidence.mean() < pattern.theta() - exitMargin) {
            state = PatternState.INACTIVE;
            cusum.reset();
            return java.util.Optional.of(PatternCause.EXIT_MEAN);
        }
        var alternative = pattern.theta() - 0.1;
        if (state == PatternState.ACTIVE && cusum.observe(success, alternative, changepointH)) {
            evidence.halve();
            return java.util.Optional.of(PatternCause.CHANGEPOINT_HALVED);
        }
        return java.util.Optional.empty();
    }
}
