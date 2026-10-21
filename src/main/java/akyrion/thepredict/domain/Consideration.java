package akyrion.thepredict.domain;

/**
 * Factors of {@link UtilityScore} (M20.6). Later modules append values; ordinals only grow.
 */
public enum Consideration {
    /** Priority over 100, floored at 0.1. */
    PRIORITY,
    /** Whether the purpose fits the movement. */
    REACH_FIT,
    /** How soon the lead choice is ready. */
    READINESS,
    /** How many declared conditions hold now. */
    BEST_WHEN,
    /** Risky skills while health is critical. */
    RISK_HEALTH,
    /** A shield the skill does not ignore. */
    SHIELD,
    /** Hysteresis for the tactic already in force. */
    COMMITMENT,
    /** Terrain of the zone. One when the zone is absent. */
    TERRAIN,
    /** Whether the tactic fits the published role. One when it fits, 0.7 otherwise, never zero (M25). */
    ROLE_FIT,
    /** A perceived pattern. Factor in {@code [1, 1.2]}, never below one (M26). */
    ENEMY_PATTERN,
    /** Learned value of the tactic in this context. Applied only by learned scoring (M28). */
    LEARNED_VALUE
}
