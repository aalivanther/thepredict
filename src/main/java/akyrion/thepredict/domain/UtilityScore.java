package akyrion.thepredict.domain;

/**
 * Fixed utility curves and Mark's compensation (M20.6). A zero vetoes. {@code scoreMillis} is
 * {@code round(1000 × product)}, clamped to 1000. Arithmetic is the strict {@code double} of Java.
 */
public final class UtilityScore {
    private UtilityScore() {
    }

    /**
     * Priority curve: {@code priority / 100}, floored at 0.1 and capped at 1.
     *
     * @param priority tactic priority
     * @return the factor
     */
    public static double priority(int priority) {
        var scaled = priority / 100.0;
        if (scaled < 0.1) {
            return 0.1;
        }
        return scaled > 1.0 ? 1.0 : scaled;
    }

    /**
     * Reach curve. {@link PurposeRules.PurposeFit#NEVER_CASTS} is a veto.
     *
     * @param fit fit of the purpose
     * @return the factor
     */
    public static double reach(PurposeRules.PurposeFit fit) {
        return switch (fit) {
            case FITS -> 1.0;
            case CASTS_ONLY_IF_TARGET_COMES -> 0.5;
            case NEVER_CASTS -> 0.0;
        };
    }

    /**
     * Readiness curve.
     *
     * @param readyInTicks ticks until the lead choice
     * @param readyHorizonTicks horizon of the question
     * @return the factor
     */
    public static double readiness(int readyInTicks, int readyHorizonTicks) {
        if (readyInTicks <= 0) {
            return 1.0;
        }
        var horizon = readyHorizonTicks <= 0 ? 1 : readyHorizonTicks;
        var scaled = 1.0 - readyInTicks / (2.0 * horizon);
        return scaled < 0.2 ? 0.2 : scaled;
    }

    /**
     * Best-when curve: {@code 0.6 + 0.2 × n}, capped at 1.
     *
     * @param matches conditions that hold
     * @return the factor
     */
    public static double bestWhen(int matches) {
        var scaled = 0.6 + 0.2 * matches;
        return scaled > 1.0 ? 1.0 : scaled;
    }

    /**
     * Risk curve: 0.3 when health is critical and the purpose leaves the caster exposed or roots it.
     *
     * @param critical whether health is critical
     * @param exposed whether the purpose has {@code LEAVES_CASTER_EXPOSED} or {@code ROOTS_CASTER}
     * @return the factor
     */
    public static double risk(boolean critical, boolean exposed) {
        return critical && exposed ? 0.3 : 1.0;
    }

    /**
     * Shield curve.
     *
     * @param blocked whether the target blocks, the skill does not ignore shields and the movement is not a flank
     * @return the factor
     */
    public static double shield(boolean blocked) {
        return blocked ? 0.5 : 1.0;
    }

    /**
     * Product of the compensated factors. A zero returns 0. Commitment, when true, multiplies by 1.15 after the
     * product and does not count as a compensated factor.
     *
     * @param factors factors in 0–1, not including commitment
     * @param committed whether the tactic is the one in force
     * @return score in 0–1000
     */
    public static int scoreMillis(double[] factors, boolean committed) {
        var count = 0;
        for (var factor : factors) {
            if (factor == 0.0) {
                return 0;
            }
            count++;
        }
        if (count == 0) {
            return 0;
        }
        var product = 1.0;
        for (var factor : factors) {
            product *= factor + (1.0 - factor) * (1.0 - 1.0 / count) * factor;
        }
        if (committed) {
            product *= 1.15;
        }
        var millis = StrictMath.round(1000.0 * product);
        if (millis < 0) {
            return 0;
        }
        return millis > 1000 ? 1000 : (int) millis;
    }

    /**
     * Role fit (M25). A tactic that fits the role scores 1. Priority scoring only reorders ties, so this never
     * returns 0.
     *
     * @param fits whether the tactic fits the published role
     * @return {@code 1.0} when it fits, otherwise {@code 0.7}
     */
    public static double roleFit(boolean fits) {
        return fits ? 1.0 : 0.7;
    }

    /**
     * Applies a pattern factor after compensation. {@code 1} leaves the score unchanged. The factor is clamped to
     * {@code [1, 1.2]} and is not one of the compensated factors.
     *
     * @param scoreMillis a score already in 0–1000
     * @param factor pattern factor
     * @return the adjusted score, still in 0–1000
     */
    public static int withPattern(int scoreMillis, double factor) {
        if (!(factor > 1.0) || scoreMillis <= 0) {
            return scoreMillis;
        }
        var clamped = factor > 1.2 ? 1.2 : factor;
        var scaled = (int) StrictMath.round(scoreMillis * clamped);
        return scaled > 1000 ? 1000 : scaled;
    }
}
