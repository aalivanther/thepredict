package akyrion.thepredict.domain;

/**
 * Closed actions of one enemy (M26.2). Declaration order is the ordinal and the tie-break. Episodes are not actions.
 */
public enum EnemyAction {
    /** No witnessed action for {@code idleTicks}. */
    IDLE,
    /** A hit that landed with a full attack charge and was not a critical or a sprint hit. */
    STRIKE,
    /** A hit that landed before the attack had recharged. */
    STRIKE_EARLY,
    /** A critical hit. A spear never takes this value. */
    CRIT_STRIKE,
    /** A sprinting hit with a full charge. A spear never takes this value. */
    SPRINT_STRIKE,
    /** The shield rose. An edge, not a level. */
    SHIELD_UP,
    /** The shield dropped. An edge, not a level. */
    SHIELD_DOWN,
    /** Strafe to the witness's left of the enemy. */
    STRAFE_LEFT,
    /** Strafe to the witness's right of the enemy. */
    STRAFE_RIGHT,
    /** Step toward the witness. */
    STEP_IN,
    /** Step away from the witness. */
    STEP_OUT,
    /** A jump. */
    JUMP,
    /** A sprint that started again. */
    SPRINT_RESET,
    /** A bow started to draw. */
    DRAW,
    /** A shot was released. */
    RELEASE,
    /** The enemy started to eat or drink. */
    CONSUME;

    /** How many actions the predictor tables. */
    public static final int COUNT = 16;

    /**
     * Whether consecutive copies of this action may merge.
     *
     * @return {@code true} for strafes and steps
     */
    public boolean merges() {
        return this == STRAFE_LEFT || this == STRAFE_RIGHT || this == STEP_IN || this == STEP_OUT;
    }

    /**
     * Whether a full token bucket drops this action before a strike.
     *
     * @return {@code true} for movement tokens
     */
    public boolean droppedBeforeStrikes() {
        return merges() || this == JUMP || this == SPRINT_RESET;
    }

    /**
     * Order of tokens that share a tick: strike, shield, movement, jump, sprint, bow, consume, idle.
     *
     * @return the rank, lower first
     */
    public int sameTickRank() {
        return switch (this) {
            case STRIKE, STRIKE_EARLY, CRIT_STRIKE, SPRINT_STRIKE -> 0;
            case SHIELD_UP, SHIELD_DOWN -> 1;
            case STRAFE_LEFT, STRAFE_RIGHT, STEP_IN, STEP_OUT -> 2;
            case JUMP -> 3;
            case SPRINT_RESET -> 4;
            case DRAW, RELEASE -> 5;
            case CONSUME -> 6;
            case IDLE -> 7;
        };
    }
}
