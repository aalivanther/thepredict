package akyrion.thepredict.domain;

/** Perceivable condition of a player. */
public enum TargetCue {
    /** The player is raising a shield. */
    RAISING_SHIELD,
    /** The shield is up and blocking. */
    BLOCKING,
    /** The shield is disabled. */
    SHIELD_DISABLED,
    /** The player's attack is recharging. */
    ATTACK_RECHARGING,
    /** The player is drawing a bow. */
    DRAWING_BOW,
    /** The bow is fully drawn. */
    BOW_FULLY_DRAWN,
    /** The player holds a loaded crossbow. */
    CROSSBOW_LOADED,
    /** The player is eating or drinking. */
    CONSUMING,
    /** The player holds a totem of undying. */
    HOLDS_TOTEM,
    /** The player's ender pearl is on cooldown. */
    PEARL_ON_COOLDOWN,
    /** The player is gliding. */
    GLIDING,
    /** The player holds a long-reach weapon. */
    LONG_REACH_WEAPON,
    /** The player is diving with a mace. */
    MACE_DIVE,
    /** The player is invisible. */
    INVISIBLE
}
