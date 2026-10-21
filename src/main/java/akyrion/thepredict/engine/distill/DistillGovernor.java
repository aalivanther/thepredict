package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DemotionCause;
import akyrion.thepredict.domain.distill.DistillMode;

import java.util.Optional;

/**
 * One-way demotion of a distilled mode (M29.7). Only an administrator command raises it, by clearing the floor.
 */
public final class DistillGovernor {
    private DistillMode floor = DistillMode.AUTONOMOUS;
    private double cusum;
    private int audits;
    private int entropyStreak;
    private Optional<DemotionCause> last = Optional.empty();

    /**
     * Starts at autonomous so a demotion has room to fall.
     */
    public DistillGovernor() {
    }

    /**
     * The lowest mode the governor still allows.
     *
     * @return the floor
     */
    public DistillMode floor() {
        return floor;
    }

    /**
     * The last cause, if a demotion happened.
     *
     * @return the cause
     */
    public Optional<DemotionCause> last() {
        return last;
    }

    /**
     * Effective mode: the minimum of configured, verdict and this floor.
     *
     * @param configured the configured mode
     * @param verdict the highest mode the verdict allows
     * @return the minimum
     */
    public DistillMode effective(DistillMode configured, DistillMode verdict) {
        return configured.lower(verdict).lower(floor);
    }

    /**
     * Clears the floor. This is the only rise, and it comes from {@code distill mode}.
     */
    public void clear() {
        floor = DistillMode.AUTONOMOUS;
        cusum = 0.0;
        last = Optional.empty();
    }

    /**
     * One audit difference against a reference of 0. An alarm at −0.05 with {@code h = 5} lowers one mode
     * after the cell has at least 20 audits.
     *
     * @param delta outcome difference, student minus audit
     * @param h CUSUM threshold
     * @return whether a mode was lowered
     */
    public boolean audit(double delta, int h) {
        audits++;
        cusum = Math.min(0.0, cusum + delta);
        if (audits < 20 || cusum > -h) {
            return false;
        }
        cusum = 0.0;
        return lower(DemotionCause.AUDIT_DRIFT, false);
    }

    /**
     * A time-to-kill or win-rate miss lowers one mode.
     *
     * @return whether a mode was lowered
     */
    public boolean outcome() {
        return lower(DemotionCause.OUTCOME_DRIFT, false);
    }

    /**
     * The entropy alarm of M22 ({@code TACTIC_ENTROPY_RATIO}): ten encounters in a row in which one tactic took more
     * than 80 percent of the adoptions lower one mode. The input is {@link DistillSession#dominantShare()}, a collapse
     * toward one tactic; the normalised entropy {@link DistillSession#tacticEntropy()} is high for varied play and
     * must not be passed here.
     *
     * @param share share of the most adopted tactic in the encounter
     * @return whether a mode was lowered
     */
    public boolean entropy(double share) {
        entropyStreak = share > 0.8 ? entropyStreak + 1 : 0;
        if (entropyStreak < 10) {
            return false;
        }
        entropyStreak = 0;
        return lower(DemotionCause.ENTROPY, false);
    }

    /**
     * A fairness alarm sends the whole profile to shadow.
     *
     * @return whether the floor changed
     */
    public boolean fairness() {
        return lower(DemotionCause.FAIRNESS, true);
    }

    private boolean lower(DemotionCause cause, boolean shadow) {
        var next = shadow ? DistillMode.SHADOW : down(floor);
        if (next == floor) {
            return false;
        }
        floor = next;
        last = Optional.of(cause);
        return true;
    }

    private static DistillMode down(DistillMode mode) {
        return switch (mode) {
            case AUTONOMOUS -> DistillMode.ASSIST;
            case ASSIST -> DistillMode.SHADOW;
            case SHADOW, OFF -> mode;
        };
    }
}
