package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.LearningSettings;

/**
 * Reward {@code r1} (M28.2). Pure. {@code β^5} is {@code (4/5)^5 = 0.32768}, not the plan's rounded 0.328
 * (errata E28-18 applied). A fight with no players has no group clock, so {@code g = 0} (errata E28-7 applied).
 */
public final class CreditMath {
    private CreditMath() {
    }

    /**
     * Normalised immediate effect, {@code clamp(effect, -2, 2) / 2}.
     *
     * @param effect window effect
     * @return the term in {@code [-1, 1]}
     */
    public static double effectTerm(int effect) {
        return effectTerm((double) effect);
    }

    /**
     * Normalised immediate effect, {@code clamp(effect, -2, 2) / 2}.
     *
     * @param effect window effect
     * @return the term in {@code [-1, 1]}
     */
    public static double effectTerm(double effect) {
        var clamped = effect < -2.0 ? -2.0 : Math.min(effect, 2.0);
        return clamped / 2.0;
    }

    /**
     * Deferred credit {@code z · β^k}.
     *
     * @param terminal terminal credit
     * @param steps windows back from the close, 0 for the last window
     * @return the credit
     */
    public static double deferred(double terminal, int steps) {
        return deferred(terminal, steps, LearningSettings.DEFAULT.creditDecay());
    }

    /**
     * Deferred credit {@code z · decay^k}. {@code learning.creditDecayPerWindow} is the decay.
     *
     * @param terminal terminal credit
     * @param steps windows back from the close, 0 for the last window
     * @param decay per-window decay
     * @return the credit
     */
    public static double deferred(double terminal, int steps, double decay) {
        var value = terminal;
        for (var step = 0; step < steps; step++) {
            value = value * decay;
        }
        return value;
    }

    /**
     * Speed score. A clock shorter than 60 ticks, or a missing reference, is 0.5.
     *
     * @param referenceTicks reference time
     * @param tookTicks measured time
     * @return {@code clamp(reference / took, 0.5, 1)}
     */
    public static double speed(int referenceTicks, int tookTicks) {
        if (tookTicks < RewardSpec.MIN_CLOCK_TICKS || referenceTicks <= 0) {
            return 0.5;
        }
        var ratio = (double) referenceTicks / tookTicks;
        if (ratio < 0.5) {
            return 0.5;
        }
        return Math.min(ratio, 1.0);
    }

    /**
     * Individual terminal credit. Death of the mob is −1 even if it also killed the target.
     *
     * @param mobDied whether the mob died
     * @param targetDied whether the credited target died
     * @param speed speed score of the owning clock
     * @return {@code z_ind}
     */
    public static double individual(boolean mobDied, boolean targetDied, double speed) {
        if (mobDied) {
            return -1.0;
        }
        return targetDied ? speed : 0.0;
    }

    /**
     * Group terminal credit. A partial win is 0.
     *
     * @param allDead every member died
     * @param won some member lives and no participant with an open group clock still lives
     * @param speed speed score
     * @return {@code z_grp}
     */
    public static double group(boolean allDead, boolean won, double speed) {
        if (allDead) {
            return -1.0;
        }
        return won ? speed : 0.0;
    }

    /**
     * Mixes the two terminals. Without a group, {@code g = 0}.
     *
     * @param individual individual credit
     * @param group group credit
     * @param grouped whether this mob is in a group of at least 2 with {@code group.mode} at least fair
     * @return {@code z}
     */
    public static double mix(double individual, double group, boolean grouped) {
        return mix(individual, group, grouped, LearningSettings.DEFAULT.groupCreditShare());
    }

    /**
     * Mixes the two terminals with {@code learning.groupCreditShare}. Without a group the share is ignored.
     *
     * @param individual individual credit
     * @param group group credit
     * @param grouped whether this mob is in a group of at least 2 with {@code group.mode} at least fair
     * @param share group share {@code g}
     * @return {@code z}
     */
    public static double mix(double individual, double group, boolean grouped, double share) {
        if (!grouped) {
            return individual;
        }
        return (1.0 - share) * individual + share * group;
    }

    /**
     * Terminal credit from one clock snapshot. Death of the mob wins. No group clock keeps {@code g = 0}.
     *
     * @param snapshot clocks read once
     * @return {@code z}
     */
    public static double terminal(CreditSnapshot snapshot) {
        var own = speed(snapshot.referenceTicks(), snapshot.elapsedTicks());
        var individual = individual(snapshot.mobDied(), snapshot.targetDied(), own);
        if (!snapshot.grouped()) {
            return individual;
        }
        var groupSpeed = speed(snapshot.referenceTicks(), snapshot.groupElapsedTicks());
        var group = group(snapshot.groupAllDead(), snapshot.groupWon(), groupSpeed);
        return mix(individual, group, true, snapshot.groupShare());
    }

    /**
     * Avoided damage of a defensive tactic, {@code clamp((ρ_before − ρ_during) · ticks, 0, 1)} (M28.2). Both rates
     * are fractions of maximum health per tick, so the product is already a fraction of the health: there is no
     * division by the health in points. A non-finite rate gives 0.
     *
     * @param before damage per tick in the 60 ticks before the window
     * @param during damage per tick during the window
     * @param ticks ticks of the window
     * @param defensive whether the tactic earns the term, see {@link RewardSpec#defensive}
     * @return {@code a} in {@code [0, 1]}
     */
    public static double avoided(double before, double during, int ticks, boolean defensive) {
        if (!defensive || ticks <= 0 || !Double.isFinite(before) || !Double.isFinite(during)) {
            return 0.0;
        }
        var raw = (before - during) * ticks;
        if (!(raw > 0.0)) {
            return 0.0;
        }
        return Math.min(raw, 1.0);
    }

    /**
     * Legacy entry point of {@link #avoided(double, double, int, boolean)}. The caller's rates are fractions of
     * maximum health per tick, so the health in points takes no part in the result.
     *
     * @param before damage per tick in the 60 ticks before the window
     * @param during damage per tick during the window
     * @param ticks ticks of the window
     * @param maxHealth ignored
     * @param defensive whether the tactic earns the term
     * @return {@code a} in {@code [0, 1]}
     */
    public static double avoided(double before, double during, int ticks, double maxHealth, boolean defensive) {
        return avoided(before, during, ticks, defensive);
    }

    /**
     * Reward {@code (1-η)·e + η·c + ζ·a}.
     *
     * @param effect immediate term
     * @param credit deferred credit
     * @param avoided avoided-damage term
     * @return {@code r1}
     */
    public static double reward(double effect, double credit, double avoided) {
        return reward(effect, credit, avoided, LearningSettings.DEFAULT.creditShare());
    }

    /**
     * Reward {@code (1-η)·e + η·c + ζ·a}. {@code η} is {@code learning.creditShare}.
     *
     * @param effect immediate term
     * @param credit deferred credit
     * @param avoided avoided-damage term
     * @param creditShare weight of the deferred credit
     * @return {@code r1}
     */
    public static double reward(double effect, double credit, double avoided, double creditShare) {
        return (1.0 - creditShare) * effect + creditShare * credit + RewardSpec.ZETA * avoided;
    }

    /**
     * Sign of the immediate part, excluding deferred credit (errata E28-10 applied).
     *
     * @param effect immediate term
     * @param avoided avoided-damage term
     * @return −1, 0 or 1
     */
    public static int immediateSign(double effect, double avoided) {
        var immediate = effect + avoided;
        if (immediate > 0.0) {
            return 1;
        }
        return immediate < 0.0 ? -1 : 0;
    }
}
