package akyrion.thepredict.domain;

/**
 * Closed catalogue of what an enemy reading may use (M26.1). Each name is a perceptible event. Inventory, food,
 * experience, raw input and a rival's goals are not in the catalogue.
 */
public enum EnemySignal {
    /** A hit that landed on a member. */
    LANDED_HIT,
    /** An air swing kept only as a diagnosis unless the policy is on. */
    AIR_SWING,
    /** The shield rose or fell. */
    SHIELD_EDGE,
    /** A sprint that started again. */
    SPRINT,
    /** A jump, read from fall distance. */
    JUMP,
    /** A bow draw or release. */
    BOW,
    /** Eating or drinking that a member could hear. */
    CONSUME,
    /** A pearl. */
    PEARL,
    /** A totem pop, audible inside the hearing range. */
    TOTEM,
    /** Movement read from the kinematic word. */
    KINEMATICS
}
