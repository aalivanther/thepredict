package akyrion.thepredict.domain;

/**
 * Time-to-kill estimate in seconds (M22.8, errata E22-7). The half-life scales with T around a 45 s reference and is
 * capped. Pure {@link StrictMath}. At a constant rate the startup correction makes the estimate the true length.
 */
public final class TtkEstimator {
    /** Fights the base half-life was chosen for, in seconds. */
    public static final double REFERENCE_SECONDS = 45.0;
    /** Lowest scaled half-life, in ticks. */
    public static final int MIN_HALF_LIFE_TICKS = 20;
    /** Highest scaled half-life, in ticks. */
    public static final int MAX_HALF_LIFE_TICKS = 600;
    /** One tick, in seconds. */
    public static final double SECONDS_PER_TICK = 0.05;

    private TtkEstimator() {
    }

    /**
     * Half-life proportional to T, clipped to [{@value #MIN_HALF_LIFE_TICKS}, {@value #MAX_HALF_LIFE_TICKS}] and to
     * a quarter of the fight.
     *
     * @param baseHalfLifeTicks the configured base
     * @param targetSeconds T
     * @return the scaled half-life, in ticks
     */
    public static int scaledHalfLifeTicks(int baseHalfLifeTicks, double targetSeconds) {
        var scaled = baseHalfLifeTicks * Math.max(targetSeconds, 1.0) / REFERENCE_SECONDS;
        var quarter = Math.max(MIN_HALF_LIFE_TICKS, targetSeconds / SECONDS_PER_TICK / 4.0);
        var capped = Math.min(MAX_HALF_LIFE_TICKS, Math.min(quarter, Math.max(MIN_HALF_LIFE_TICKS, scaled)));
        return (int) StrictMath.round(capped);
    }

    /**
     * Integrator after one hit. {@code S ← S·2^(−Δt/h) + x}.
     *
     * @param integrator S
     * @param previousTick tick of the previous update
     * @param tick tick of the hit
     * @param damageFraction health fraction of this hit
     * @param halfLifeTicks half-life
     * @return the new S
     */
    public static double hit(double integrator, long previousTick, long tick, double damageFraction,
                             int halfLifeTicks) {
        return decay(integrator, previousTick, tick, halfLifeTicks) + damageFraction;
    }

    /**
     * Decays S to a tick without adding a hit.
     *
     * @param integrator S
     * @param previousTick tick of the previous update
     * @param tick tick of the check
     * @param halfLifeTicks half-life
     * @return the decayed S
     */
    public static double decay(double integrator, long previousTick, long tick, int halfLifeTicks) {
        if (halfLifeTicks < 1 || tick <= previousTick) {
            return integrator;
        }
        return integrator * StrictMath.pow(2.0, -((double) (tick - previousTick)) / halfLifeTicks);
    }

    /**
     * Projects the time to kill.
     *
     * @param integrator S already decayed to {@code tick}
     * @param dealtFraction D, health fraction dealt since engage
     * @param healthRemaining H, health fraction still on the target
     * @param engagedTick tick of the first hit
     * @param tick tick of the check
     * @param halfLifeTicks half-life
     * @param minEvidenceTicks minimum age
     * @param targetSeconds T
     * @param capRatio cap as a multiple of T
     * @return the estimate; {@code valid} is false until the evidence gates pass
     */
    public static Estimate project(double integrator, double dealtFraction, double healthRemaining, long engagedTick,
                                   long tick, int halfLifeTicks, int minEvidenceTicks, double targetSeconds,
                                   double capRatio) {
        var age = tick - engagedTick;
        var ageSeconds = age * SECONDS_PER_TICK;
        var valid = age >= minEvidenceTicks && dealtFraction >= 0.05 && halfLifeTicks > 0 && ageSeconds > 0.0;
        if (!valid) {
            return new Estimate(0.0, 0.0, false);
        }
        var startup = 1.0 - StrictMath.pow(2.0, -((double) age) / halfLifeTicks);
        var perTick = integrator * StrictMath.log(2.0) / (halfLifeTicks * startup);
        var perSecond = perTick / SECONDS_PER_TICK;
        var floor = 0.5 * dealtFraction / ageSeconds;
        var rate = Math.max(perSecond, floor);
        var projected = ageSeconds + (rate > 0.0 ? healthRemaining / rate : capRatio * targetSeconds);
        var capped = Math.min(projected, capRatio * targetSeconds);
        return new Estimate(capped, perSecond, true);
    }

    /**
     * One projection.
     *
     * @param seconds projected time to kill, in seconds
     * @param ratePerSecond rate used, health fraction per second
     * @param valid whether the evidence gates passed
     */
    public record Estimate(double seconds, double ratePerSecond, boolean valid) {
    }
}
