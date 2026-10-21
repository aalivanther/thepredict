package akyrion.thepredict.domain;

/**
 * Fast and slow loops (M22.8). The fast loop moves the rung by one after two checks on the same side of the band.
 * The slow loop moves the prior by at most one. A step limit keeps a gain of 2.2 inside the ladder.
 */
public final class DifficultyPolicy {
    /** Lowest rung. */
    public static final int MIN_RUNG = 0;
    /** Highest rung. */
    public static final int MAX_RUNG = 8;

    private DifficultyPolicy() {
    }

    /**
     * Fast-loop state.
     *
     * @param rung current rung
     * @param outsideStreak consecutive checks on the current side
     * @param sign side of the last outside check, {@code +1} too slow, {@code -1} too fast, {@code 0} none
     */
    public record Fast(int rung, int outsideStreak, int sign) {
    }

    /**
     * One fast check. An invalid check or a check with no hit leaves the streak untouched. A busy caster defers the
     * step and keeps the streak.
     *
     * @param state the state
     * @param projectedSeconds estimated time to kill
     * @param targetSeconds T
     * @param tolerance band ratio
     * @param evidence whether the check counts
     * @param busy whether the caster is still winding up
     * @param minRung lowest allowed rung
     * @param maxRung highest allowed rung
     * @return the next state
     */
    public static Fast fast(Fast state, double projectedSeconds, double targetSeconds, double tolerance,
                            boolean evidence, boolean busy, int minRung, int maxRung) {
        if (!evidence || !(targetSeconds > 0.0) || !(projectedSeconds > 0.0)) {
            return state;
        }
        var band = StrictMath.log(1.0 + tolerance);
        var gap = StrictMath.log(projectedSeconds / targetSeconds);
        if (Math.abs(gap) <= band) {
            return new Fast(state.rung, 0, 0);
        }
        var sign = gap > 0.0 ? 1 : -1;
        var streak = sign == state.sign ? state.outsideStreak + 1 : 1;
        if (streak < 2 || busy) {
            return new Fast(state.rung, streak, sign);
        }
        var next = Math.max(minRung, Math.min(maxRung, state.rung + sign));
        return new Fast(next, 0, sign);
    }

    /**
     * Change of the prior. {@code |Δu| ≤ 1} when {@code clampStep} is set. Without the clamp a gain of 2.2 walks off
     * the ladder.
     *
     * @param logTtk {@code y}, log of the observed time
     * @param logTarget {@code ln T}
     * @param deadbandRatio band inside which the prior stays
     * @param gain effective gain
     * @param kHat ladder gain
     * @param clampStep whether the step is limited to 1
     * @return the delta, or 0 inside the band
     */
    public static double deltaU(double logTtk, double logTarget, double deadbandRatio, double gain, double kHat,
                                boolean clampStep) {
        var gap = logTarget - logTtk;
        if (Math.abs(gap) <= StrictMath.log(1.0 + deadbandRatio)) {
            return 0.0;
        }
        var step = -(gain / kHat) * gap;
        return clampStep ? clamp(step, -1.0, 1.0) : step;
    }

    /**
     * Next prior. A step that is not finite does not move the prior (rule A6), so a NaN never poisons it.
     *
     * @param mean time-weighted prior of the fight
     * @param delta the step
     * @param min lowest prior
     * @param max highest prior
     * @return the clamped prior
     */
    public static double nextU(double mean, double delta, double min, double max) {
        return clamp(Double.isFinite(delta) ? mean + delta : mean, min, max);
    }

    /**
     * How many times to halve the gain. Four reversals in ten fights halve once; the floor is three halvings (1/8).
     * Three reversals do not damp.
     *
     * @param reversals sign changes in the last ten fights
     * @param needed reversals that cause one halving
     * @return halvings, 0 to 3
     */
    public static int dampings(int reversals, int needed) {
        if (reversals < needed) {
            return 0;
        }
        return Math.min(3, 1 + (reversals - needed) / needed);
    }

    /**
     * Gain after damping.
     *
     * @param gain the undamped gain
     * @param halvings how many times it was halved
     * @return the gain
     */
    public static double dampedGain(double gain, int halvings) {
        var value = gain;
        for (var step = 0; step < halvings; step++) {
            value *= 0.5;
        }
        return value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
