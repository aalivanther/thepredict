package akyrion.thepredict.domain;

/** A drawback of a skill, declared by the author in {@code Risks} (M15.1). */
public enum SkillRisk {
    /** Leaves the caster exposed afterwards. */
    LEAVES_CASTER_EXPOSED,
    /** The caster cannot move while using it. */
    ROOTS_CASTER,
    /** Hurts the caster. */
    SELF_DAMAGE,
    /** Needs a clear line of sight to the target; checked right before the cast (M15.6). */
    NEEDS_LINE_OF_SIGHT,
    /** A moving target can dodge it. */
    SLOW_PROJECTILE,
    /** A raised shield blocks it. */
    BLOCKED_BY_SHIELD,
    /** It can also hit allies. */
    HITS_ALLIES
}
