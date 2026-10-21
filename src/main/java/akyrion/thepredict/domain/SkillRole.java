package akyrion.thepredict.domain;

/**
 * Tactical role of a skill: what the author uses it for (M15.1). Closed set; it reaches Jev only as the fixed phrases
 * of {@code adapter.jev} (rule I12).
 */
public enum SkillRole {
    /** Its main purpose is to take health away, such as {@code damage{a=6} @target}. */
    DAMAGE,
    /** Stuns, slows, pushes or roots the target, such as {@code stun} or {@code throw}. */
    CONTROL,
    /** Closes the distance at once, such as {@code leap} or {@code lunge} toward the target. */
    GAP_CLOSE,
    /** Gets the caster out of danger at once, such as a backward {@code leap} or {@code teleport}. */
    ESCAPE,
    /** Reduces the damage the caster takes, such as {@code shield} or resistance on itself. */
    DEFENSE,
    /** Restores the caster's health, such as {@code heal @self}. */
    SUSTAIN,
    /** Improves the caster or its allies, such as strength on itself. */
    BUFF,
    /** Weakens the target, such as poison, bad omen or weakness. */
    DEBUFF,
    /** Creates allies, such as {@code summon}. */
    SUMMON,
    /** Denies an area for a while, such as an area effect cloud or a totem. */
    ZONE,
    /** Cuts what the target is doing, such as drinking or drawing a bow. */
    INTERRUPT,
    /** Finishes off a weakened target. */
    FINISHER,
    /** Opens the fight with an advantage, such as an ambush or a charged first hit. */
    OPENER
}
