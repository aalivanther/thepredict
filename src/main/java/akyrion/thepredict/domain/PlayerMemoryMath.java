package akyrion.thepredict.domain;

/**
 * Forgetting, cross-fight CUSUM and weighted moments (M27.2). Time enters as a day delta. Ties break by index.
 */
public final class PlayerMemoryMath {
    /** Breaks applied in one fight. */
    public static final int MAX_BREAKS = 4;
    /** Stored CUSUM step. */
    public static final double CUSUM_STEP = 0.1;
    /** Float16 magnitude used as the saturation cap. */
    public static final float SATURATION = 65504f;
    /** Habit rate the cross-fight alternative is measured against. */
    public static final double HABIT_THETA = 0.6;

    private PlayerMemoryMath() {
    }

    /**
     * Forgetting of a day gap. Negative gaps count as zero (N2).
     *
     * @param deltaDays days since the record, possibly negative
     * @param halfLifeDays half life in days
     * @return {@code 2^(-max(0, Δd) / H)}
     */
    public static double tau(long deltaDays, double halfLifeDays) {
        var days = Math.max(0L, deltaDays);
        return StrictMath.pow(2.0, -days / halfLifeDays);
    }

    /**
     * One cross-fight update. The CUSUM runs on the unscaled memory, then the fight is added after {@code gamma}.
     *
     * @param successes remembered successes
     * @param failures remembered failures
     * @param cusum remembered statistic
     * @param fightSuccesses successes of this fight
     * @param fightFailures failures of this fight
     * @param gamma per-fight decay
     * @param threshold CUSUM threshold {@code h}
     * @param rho break factor
     * @param theta habit threshold
     * @return the updated masses, the new statistic and the break count
     */
    public static CrossFight update(double successes, double failures, double cusum, int fightSuccesses,
                                    int fightFailures, double gamma, double threshold, double rho, double theta) {
        var total = successes + failures;
        var next = cusum;
        var breaks = 0;
        var keptS = successes;
        var keptF = failures;
        if (total >= 2.0 && successes / total >= theta) {
            var habit = clipHabit(successes / total);
            var alternative = theta - 0.1;
            next = StrictMath.max(0.0, next + fightSuccesses * StrictMath.log(alternative / habit)
                    + fightFailures * StrictMath.log((1.0 - alternative) / (1.0 - habit)));
            if (next > threshold) {
                breaks = Math.min(MAX_BREAKS, (int) StrictMath.floor(next / threshold));
                var factor = StrictMath.pow(rho, breaks);
                keptS *= factor;
                keptF *= factor;
                next = 0.0;
            }
        } else {
            next = 0.0;
        }
        return new CrossFight(gamma * keptS + fightSuccesses, gamma * keptF + fightFailures, next, breaks);
    }

    /**
     * Stores a CUSUM statistic as an unsigned byte with step {@link #CUSUM_STEP}.
     *
     * @param statistic the statistic
     * @return the byte
     */
    public static int cusumByte(double statistic) {
        var steps = (int) StrictMath.round(statistic / CUSUM_STEP);
        return Math.max(0, Math.min(255, steps));
    }

    /**
     * Restores a CUSUM byte.
     *
     * @param stored the byte
     * @return the statistic
     */
    public static double cusumValue(int stored) {
        return (stored & 0xFF) * CUSUM_STEP;
    }

    /**
     * The day that must be stored. It never moves backward.
     *
     * @param stored the stored day
     * @param observed the observed day
     * @return the maximum
     */
    public static long forwardDay(long stored, long observed) {
        return Math.max(stored, observed);
    }

    /**
     * Weighted Chan update of a mean and a second moment.
     *
     * @param weight current weight
     * @param mean current mean
     * @param moment current second moment
     * @param addWeight weight to add
     * @param addMean mean to add
     * @param addMoment second moment to add
     * @return weight, mean and moment
     */
    public static double[] welford(double weight, double mean, double moment, double addWeight, double addMean,
                                   double addMoment) {
        var next = weight + addWeight;
        if (next <= 0.0 || addWeight <= 0.0) {
            return new double[] {weight, mean, moment};
        }
        var delta = addMean - mean;
        var share = addWeight / next;
        return new double[] {next, mean + delta * share, moment + addMoment + delta * delta * weight * share};
    }

    /**
     * One rung step, stored as sixteenths. A missing rung stays missing on a death. A stored rung moves by at most one.
     *
     * @param stored the stored byte, {@code 255} when the band was never seen
     * @param estimate the rung estimate, 0 to 8
     * @param death whether the player died
     * @return the byte to store
     */
    public static int stepRung(int stored, float estimate, boolean death) {
        var target = Math.round(Math.max(0f, Math.min(8f, estimate)) * 16f);
        if (stored == ProfileMemory.NONE) {
            return death ? ProfileMemory.NONE : target;
        }
        var current = stored / 16;
        var wanted = target / 16;
        if (death) {
            return wanted >= current ? stored : (current - 1) * 16;
        }
        if (wanted > current) {
            return (current + 1) * 16;
        }
        if (wanted < current) {
            return (current - 1) * 16;
        }
        return stored;
    }

    /**
     * Clamps a mass to the float16 range.
     *
     * @param value the mass
     * @return the clamped mass
     */
    public static float saturate(float value) {
        if (value < 0f) {
            return 0f;
        }
        return value > SATURATION ? SATURATION : value;
    }

    /**
     * Whether {@link #saturate} would change the value.
     *
     * @param value the mass
     * @return {@code true} when the value is outside {@code [0, 65504]}
     */
    public static boolean saturated(float value) {
        return value < 0f || value > SATURATION;
    }

    /**
     * Alternative rate {@code θ − 0.1}.
     *
     * @return the rate
     */
    public static double p1() {
        return HABIT_THETA - 0.1;
    }

    private static double clipHabit(double habit) {
        return Math.min(0.95, Math.max(0.55, habit));
    }

    /**
     * One cross-fight step.
     *
     * @param successes updated successes
     * @param failures updated failures
     * @param cusum updated statistic
     * @param breaks breaks applied, at most 4
     */
    public record CrossFight(double successes, double failures, double cusum, int breaks) {
    }
}
