package akyrion.thepredict.domain;

/**
 * A situation in which a skill works best, declared by the author in {@code BestWhen} (M15.1); {@link
 * ActiveUseConditions} tells which ones hold in a capture.
 */
public enum UseCondition {
    /** The target's health is critical. */
    TARGET_LOW_HEALTH,
    /** The target is blocking or raising a shield. */
    TARGET_SHIELDING,
    /** The target is backing away and is not in melee. */
    TARGET_FLEEING,
    /** The caster's health is critical. */
    SELF_LOW_HEALTH,
    /** The target stands close to other enemies. */
    ENEMIES_GROUPED,
    /** The target is outside melee reach but within ranged reach. */
    TARGET_AT_RANGE,
    /** The target is inside melee reach. */
    TARGET_IN_MELEE
}
