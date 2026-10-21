package akyrion.thepredict.domain;

/**
 * Closed episodes of one enemy (M26.2). They are not tokens of the action sequence. There are ten (errata E26-4).
 */
public enum EnemyEpisode {
    /** A potion was finished. */
    HEAL_CONSUMED,
    /** Food was finished. */
    FOOD_CONSUMED,
    /** An ender pearl was thrown. */
    PEARL,
    /** A totem saved the enemy. */
    TOTEM_POP,
    /** The shield broke. */
    SHIELD_BROKEN,
    /** The main-hand weapon changed. */
    WEAPON_SWAP,
    /** A piece of armor broke. */
    ARMOR_BROKEN,
    /** A mace smash landed. */
    MACE_SMASH,
    /** A wind charge launched the enemy. */
    WIND_LAUNCH,
    /** A burst of mobility (riptide or a similar visible burst). */
    MOBILITY_BURST;

    /** How many episode rates exist. */
    public static final int COUNT = 10;
}
