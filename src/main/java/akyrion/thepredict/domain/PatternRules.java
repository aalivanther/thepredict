package akyrion.thepredict.domain;

/**
 * Closed occasions of the pattern table v1 (M26.4). Thresholds live on {@link EnemyPattern}. They are not
 * configuration keys. {@link EnemyPattern#FIXED_CADENCE} is not a beta detector.
 */
public final class PatternRules {
    /** Table version. A change is a {@code CODE_PRESET}. */
    public static final int VERSION = 1;

    private PatternRules() {
    }

    /**
     * Whether the pattern is a beta proportion.
     *
     * @param pattern the pattern
     * @return {@code false} for cadence
     */
    public static boolean usesBeta(EnemyPattern pattern) {
        return pattern != EnemyPattern.FIXED_CADENCE;
    }

    /**
     * Activation threshold.
     *
     * @param pattern the pattern
     * @return theta
     */
    public static double theta(EnemyPattern pattern) {
        return pattern.theta();
    }

    /**
     * Family of the pattern.
     *
     * @param pattern the pattern
     * @return the group
     */
    public static PatternGroup group(EnemyPattern pattern) {
        return pattern.group();
    }

    /**
     * Whether a token is the success of a rhythm pattern that ignores spears.
     *
     * @param pattern the pattern
     * @param action the token
     * @param spear whether the weapon was a spear
     * @return whether it counts as a success
     */
    public static boolean rhythmSuccess(EnemyPattern pattern, EnemyAction action, boolean spear) {
        if (spear && (pattern == EnemyPattern.SPAMS_CLICKS || pattern == EnemyPattern.CRIT_JUMPER)) {
            return false;
        }
        return switch (pattern) {
            case SPAMS_CLICKS -> action == EnemyAction.STRIKE_EARLY;
            case CRIT_JUMPER -> action == EnemyAction.CRIT_STRIKE;
            case WTAP_PRESSURE -> action == EnemyAction.SPRINT_RESET;
            default -> false;
        };
    }

    /**
     * Whether a ranged entity that stays out of melee is the kite occasion.
     *
     * @param rangedShots the entity shoots
     * @param staysBeyondMelee it does not step into melee
     * @return {@code true} for {@link EnemyPattern#KITES_AT_RANGE}
     */
    public static boolean kites(boolean rangedShots, boolean staysBeyondMelee) {
        return rangedShots && staysBeyondMelee;
    }

    /**
     * Whether a skeleton that shoots and stays out of melee is {@link EnemyPattern#KITES_AT_RANGE}.
     *
     * @param skeleton the entity is a skeleton
     * @param shoots it fires a projectile
     * @param staysBeyondMelee it does not step into melee
     * @return {@code true} only for all three
     */
    public static boolean kitesSkeleton(boolean skeleton, boolean shoots, boolean staysBeyondMelee) {
        return skeleton && kites(shoots, staysBeyondMelee);
    }

    /**
     * Whether this token is the positive occasion of the pattern. The negative occasion is any other token.
     * {@link EnemyPattern#FIXED_CADENCE} has none.
     *
     * @param pattern the pattern
     * @param action the token
     * @return {@code true} when the token is that pattern's occasion
     */
    public static boolean matches(EnemyPattern pattern, EnemyAction action) {
        return action == occasion(pattern);
    }

    /**
     * The token that counts as the pattern's own occasion. Cadence has none and returns idle, which never matches
     * a positive trial by itself.
     *
     * @param pattern the pattern
     * @return the occasion
     */
    public static EnemyAction occasion(EnemyPattern pattern) {
        return switch (pattern) {
            case SPAMS_CLICKS -> EnemyAction.STRIKE_EARLY;
            case CRIT_JUMPER -> EnemyAction.CRIT_STRIKE;
            case WTAP_PRESSURE -> EnemyAction.SPRINT_RESET;
            case PREDICTABLE_STRAFE_LEFT -> EnemyAction.STRAFE_LEFT;
            case PREDICTABLE_STRAFE_RIGHT -> EnemyAction.STRAFE_RIGHT;
            case CHASES_RETREAT -> EnemyAction.STEP_IN;
            case SHIELD_TURTLE, SHIELD_LATE -> EnemyAction.SHIELD_UP;
            case OVEREXTENDS_AFTER_BLOCK -> EnemyAction.STRIKE;
            case NO_SHIELD -> EnemyAction.STRIKE;
            case FAILS_ANSWER_BLOCK -> EnemyAction.SHIELD_DOWN;
            case FAILS_ANSWER_DODGE -> EnemyAction.STEP_OUT;
            case FAILS_ANSWER_BACK_OFF -> EnemyAction.STEP_OUT;
            case FAILS_ANSWER_BREAK_SIGHT -> EnemyAction.STEP_OUT;
            case FAILS_ANSWER_INTERRUPT -> EnemyAction.STRIKE;
            case FAILS_DODGE_PROJECTILE -> EnemyAction.RELEASE;
            case WEAK_TO_AREA -> EnemyAction.STRIKE;
            case HEALS_AT_LOW_HP, RELIES_ON_TOTEM -> EnemyAction.CONSUME;
            case PEARLS_WHEN_LOW -> EnemyAction.JUMP;
            case RETREATS_WHEN_LOW -> EnemyAction.STEP_OUT;
            case KITES_AT_RANGE -> EnemyAction.RELEASE;
            case SEEKS_HIGH_GROUND -> EnemyAction.JUMP;
            case MACE_DIVER -> EnemyAction.CRIT_STRIKE;
            case FIXED_CADENCE -> EnemyAction.IDLE;
        };
    }
}
