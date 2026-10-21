package akyrion.thepredict.domain;

/**
 * Beta evidence with forgetting (M26.3). The prior {@code Beta(2, 2)} stays outside {@code s} and {@code f}, which
 * start at zero. Updates allocate nothing.
 */
public final class BetaEvidence {
    /** Prior shape on each side. */
    public static final double PRIOR = 2.0;

    private double successes;
    private double failures;
    private int trials;
    private int seeded;

    /**
     * Evidence that still holds only the prior.
     */
    public BetaEvidence() {
    }

    /**
     * Forgets and adds one Bernoulli trial. Allocation-free.
     *
     * @param success whether the pattern was shown
     * @param lambda forgetting in {@code (0, 1]}
     * @throws IllegalArgumentException if {@code lambda} is outside {@code (0, 1]}; the evidence is left unchanged
     */
    public void update(boolean success, double lambda) {
        if (!(lambda > 0.0 && lambda <= 1.0)) {
            throw new IllegalArgumentException("lambda must be within (0, 1], was " + lambda);
        }
        successes = lambda * successes + (success ? 1.0 : 0.0);
        failures = lambda * failures + (success ? 0.0 : 1.0);
        trials++;
    }

    /**
     * Halves both counts after a changepoint. The prior is not halved.
     */
    public void halve() {
        successes *= 0.5;
        failures *= 0.5;
    }

    /**
     * Posterior mean.
     *
     * @return {@code (2 + s) / (4 + s + f)}
     */
    public double mean() {
        return (PRIOR + successes) / (PRIOR + PRIOR + successes + failures);
    }

    /**
     * Posterior success shape.
     *
     * @return {@code 2 + s}
     */
    public double alpha() {
        return PRIOR + successes;
    }

    /**
     * Posterior failure shape.
     *
     * @return {@code 2 + f}
     */
    public double beta() {
        return PRIOR + failures;
    }

    /**
     * Trials seen, before forgetting. The detector's {@code n} is this count.
     *
     * @return the number of updates
     */
    public int trials() {
        return trials;
    }

    /**
     * Adds seed mass. It counts toward the detector's {@code n} and not toward own-fight trials.
     *
     * @param successMass remembered successes, finite and {@code >= 0}
     * @param failureMass remembered failures, finite and {@code >= 0}
     * @throws IllegalArgumentException if a mass is negative, NaN or infinite; the evidence is left unchanged
     */
    public void seed(double successMass, double failureMass) {
        DomainChecks.requireNonNegative(successMass, "successMass");
        DomainChecks.requireNonNegative(failureMass, "failureMass");
        successes += successMass;
        failures += failureMass;
        seeded += (int) Math.round(successMass + failureMass);
    }

    /**
     * Pseudo-trials contributed by {@link #seed}.
     *
     * @return the rounded seed mass
     */
    public int seededTrials() {
        return seeded;
    }

    /**
     * Trials the detector may count: own trials plus seed.
     *
     * @return the sum
     */
    public int detectorTrials() {
        return trials + seeded;
    }

    /**
     * Raw success mass, without the prior.
     *
     * @return {@code s}
     */
    public double successes() {
        return successes;
    }

    /**
     * Raw failure mass, without the prior.
     *
     * @return {@code f}
     */
    public double failures() {
        return failures;
    }
}
