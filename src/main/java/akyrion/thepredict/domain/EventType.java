package akyrion.thepredict.domain;

/** Kind of event recorded in the encounter history. */
public enum EventType {
    /** A melee hit landed. */
    MELEE_HIT,
    /** A melee hit was blocked. */
    MELEE_BLOCKED,
    /** A projectile hit a mob. */
    RANGED_HIT_ON_MOB,
    /** A projectile missed. */
    RANGED_MISS,
    /** A player used a totem of undying. */
    TOTEM_USED,
    /** A player threw an ender pearl. */
    PEARL_THROWN,
    /** A player consumed a potion. */
    POTION_CONSUMED,
    /** A player's shield was disabled. */
    SHIELD_DISABLED,
    /** A player assisted another participant. */
    ALLY_ASSISTED,
    /** The group assigned this participant as a member's target (M25). The weight is one. */
    GROUP_ASSIGNMENT
}
