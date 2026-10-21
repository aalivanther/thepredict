package akyrion.thepredict.domain;

/**
 * The levers of ladder v4 (M22.7, M25). They change rhythm only: delay, which reactions arm, slack on a damaging
 * cast, whether damage breaks a tie, a local penalty, and the group caps of A13. They do not change stats or the
 * question sent to Jev. Solo rungs keep two melee attackers and a gap of six, which is no tighter than A13.
 *
 * @param reactionDelayTicks delay the trigger must already have held
 * @param reactionMask reactions that may arm, bits by {@link Reaction} ordinal
 * @param damagingCastSlackTicks extra ticks a damaging cast waits after it is otherwise ready
 * @param damagePreference whether a higher damage tier wins a ready-cast tie
 * @param localDamagePenaltyMillis penalty subtracted from a HIGH local score, 0 when off
 * @param maxMeleeAttackers melee weight against one player, 1 to 4, never above the configured cap
 * @param impactGapTicks ticks between reserved impacts, never below {@code 4}
 * @param exploitShare quota of pattern-motivated casts, 0 to 1; older constructors pass 0
 */
public record PressureLevers(int reactionDelayTicks, long reactionMask, int damagingCastSlackTicks,
                             boolean damagePreference, int localDamagePenaltyMillis, int maxMeleeAttackers,
                             int impactGapTicks, double exploitShare) {
    /**
     * Bench identity of M20: no added delay, every reaction, no slack, damage still breaks ties, no penalty.
     * A profile delay is applied by the caller as {@code max(profile, reactionDelayTicks)}.
     */
    public static final PressureLevers BENCH = new PressureLevers(0, ReactionMask.ALL
            & ~ReactionMask.bit(Reaction.HAZARD_PUSH), 0, true, 0);

    /**
     * Levers from before the group columns (rule W5). Melee stays at 2 and the gap at 6, the A13 defaults.
     *
     * @param reactionDelayTicks delay the trigger must already have held
     * @param reactionMask reactions that may arm
     * @param damagingCastSlackTicks extra ticks a damaging cast waits
     * @param damagePreference whether a higher damage tier wins a tie
     * @param localDamagePenaltyMillis penalty subtracted from a HIGH local score
     */
    public PressureLevers(int reactionDelayTicks, long reactionMask, int damagingCastSlackTicks,
                          boolean damagePreference, int localDamagePenaltyMillis) {
        this(reactionDelayTicks, reactionMask, damagingCastSlackTicks, damagePreference, localDamagePenaltyMillis,
                2, 6, 0.0);
    }

    /**
     * Levers from before the exploit column (rule W5). The share stays 0, which is the previous behavior.
     *
     * @param reactionDelayTicks delay the trigger must already have held
     * @param reactionMask reactions that may arm
     * @param damagingCastSlackTicks extra ticks a damaging cast waits
     * @param damagePreference whether a higher damage tier wins a tie
     * @param localDamagePenaltyMillis penalty subtracted from a HIGH local score
     * @param maxMeleeAttackers melee weight
     * @param impactGapTicks ticks between reserved impacts
     */
    public PressureLevers(int reactionDelayTicks, long reactionMask, int damagingCastSlackTicks,
                          boolean damagePreference, int localDamagePenaltyMillis, int maxMeleeAttackers,
                          int impactGapTicks) {
        this(reactionDelayTicks, reactionMask, damagingCastSlackTicks, damagePreference, localDamagePenaltyMillis,
                maxMeleeAttackers, impactGapTicks, 0.0);
    }

    /**
     * Checks the ranges.
     *
     * @throws IllegalArgumentException if a tick count or the penalty is negative, or a group column is outside A13
     */
    public PressureLevers {
        if (reactionDelayTicks < 0 || damagingCastSlackTicks < 0 || localDamagePenaltyMillis < 0) {
            throw new IllegalArgumentException("levers must be >= 0");
        }
        if (maxMeleeAttackers < 1 || maxMeleeAttackers > 4) {
            throw new IllegalArgumentException("maxMeleeAttackers must be within [1, 4], was " + maxMeleeAttackers);
        }
        if (impactGapTicks < 4 || impactGapTicks > 40) {
            throw new IllegalArgumentException("impactGapTicks must be within [4, 40], was " + impactGapTicks);
        }
        if (!(exploitShare >= 0.0) || exploitShare > 1.0) {
            throw new IllegalArgumentException("exploitShare must be within [0, 1], was " + exploitShare);
        }
    }
}
