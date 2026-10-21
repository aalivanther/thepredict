package akyrion.thepredict.domain;

/**
 * Ordered reactions of v1 (M20.4). Declaration order is the tie-break: an earlier reaction replaces a later one in
 * the same slot. Cooldowns are in ticks.
 */
public enum Reaction {
    /** Health is critical and the target is in melee. Survival preference and, in {@code FULL}, a backstep. */
    EVADE_CRITICAL(ReactionBand.SURVIVAL, 40, 10),
    /** A shot is incoming or drawn. Movement only; not evaluated in {@code REACTIONS}. */
    DODGE_SHOT(ReactionBand.SURVIVAL, 20, 6),
    /** The target is consuming. Opportunity preference and, in {@code FULL}, an enter. */
    PUNISH_CONSUMING(ReactionBand.OPPORTUNITY, 20, 10),
    /** A player is recharging an attack inside the inner band. */
    PUNISH_RECHARGE(ReactionBand.OPPORTUNITY, 10, 10),
    /** The target is blocking and the movement is not a flank. */
    RESPECT_SHIELD(ReactionBand.OPPORTUNITY, 10, 10),
    /** The target is critical. A finisher is preferred. */
    FINISH(ReactionBand.OPPORTUNITY, 20, 10),
    /** Open space after a cast. Movement only; not evaluated in {@code REACTIONS}. */
    SPACE_OUT(ReactionBand.SPACING, 20, 4),
    /** The mob stands on a lethal edge. Movement only; not evaluated in {@code REACTIONS}. */
    EDGE_DANGER(ReactionBand.SURVIVAL, 40, 8),
    /** An entity can be pushed into a hazard. Off unless the profile exploits that push. */
    HAZARD_PUSH(ReactionBand.OPPORTUNITY, 20, 10),
    /** A perceived enemy pattern. Last opportunity reaction. Armed only in bias or full by the exploit policy. */
    EXPLOIT_PATTERN(ReactionBand.OPPORTUNITY, 20, 10);

    private final ReactionBand band;
    private final int cooldownTicks;
    private final int preferenceTicks;

    Reaction(ReactionBand band, int cooldownTicks, int preferenceTicks) {
        this.band = band;
        this.cooldownTicks = cooldownTicks;
        this.preferenceTicks = preferenceTicks;
    }

    /**
     * Band of this reaction.
     *
     * @return the band
     */
    public ReactionBand band() {
        return band;
    }

    /**
     * Ticks before the same reaction may arm again.
     *
     * @return the cooldown
     */
    public int cooldownTicks() {
        return cooldownTicks;
    }

    /**
     * Ticks a cast preference stays armed. Strictly positive, so the slot is already valid on the tick that arms it.
     *
     * @return the preference lifetime
     */
    public int preferenceTicks() {
        return preferenceTicks;
    }

    /**
     * Tells whether this reaction is evaluated when the layer does not move the mob.
     *
     * @return {@code false} for dodge, space-out and the edge step
     */
    public boolean evaluatedWithoutMicro() {
        return this != DODGE_SHOT && this != SPACE_OUT && this != EDGE_DANGER;
    }
}
