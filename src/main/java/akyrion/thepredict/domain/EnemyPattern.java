package akyrion.thepredict.domain;

/**
 * Closed patterns of one enemy (M26.4). Ordinal 24 is {@link #FIXED_CADENCE}, entities only, with no threshold.
 * Thresholds are versioned constants, not configuration keys.
 */
public enum EnemyPattern {
    /** Swings before the attack has recharged. */
    SPAMS_CLICKS(PatternGroup.RHYTHM, 0.60),
    /** Jumps in to land critical hits. */
    CRIT_JUMPER(PatternGroup.RHYTHM, 0.50),
    /** Re-sprints between hits. */
    WTAP_PRESSURE(PatternGroup.RHYTHM, 0.60),
    /** Strafes to its left in melee. */
    PREDICTABLE_STRAFE_LEFT(PatternGroup.MOVEMENT, 0.60),
    /** Strafes to its right in melee. */
    PREDICTABLE_STRAFE_RIGHT(PatternGroup.MOVEMENT, 0.60),
    /** Chases a foe that steps back. */
    CHASES_RETREAT(PatternGroup.MOVEMENT, 0.60),
    /** Keeps the shield raised most of the time. */
    SHIELD_TURTLE(PatternGroup.DEFENSE, 0.50),
    /** Raises the shield too late. */
    SHIELD_LATE(PatternGroup.DEFENSE, 0.60),
    /** Strikes right after blocking. */
    OVEREXTENDS_AFTER_BLOCK(PatternGroup.DEFENSE, 0.60),
    /** Fights without a shield. */
    NO_SHIELD(PatternGroup.DEFENSE, 0.80),
    /** Fails to block a telegraphed attack. */
    FAILS_ANSWER_BLOCK(PatternGroup.TELLS, 0.60),
    /** Fails to dodge a telegraphed attack. */
    FAILS_ANSWER_DODGE(PatternGroup.TELLS, 0.60),
    /** Fails to back away from a telegraphed attack. */
    FAILS_ANSWER_BACK_OFF(PatternGroup.TELLS, 0.60),
    /** Fails to break line of sight in time. */
    FAILS_ANSWER_BREAK_SIGHT(PatternGroup.TELLS, 0.60),
    /** Fails to interrupt a telegraphed attack. */
    FAILS_ANSWER_INTERRUPT(PatternGroup.TELLS, 0.60),
    /** Is often hit by projectiles. */
    FAILS_DODGE_PROJECTILE(PatternGroup.TELLS, 0.60),
    /** Is often caught by area attacks. */
    WEAK_TO_AREA(PatternGroup.TELLS, 0.60),
    /** Heals when health is low. */
    HEALS_AT_LOW_HP(PatternGroup.RESOURCES, 0.60),
    /** Escapes with an ender pearl when hurt. */
    PEARLS_WHEN_LOW(PatternGroup.RESOURCES, 0.50),
    /** Retreats when health is low. */
    RETREATS_WHEN_LOW(PatternGroup.RESOURCES, 0.60),
    /** Has survived with a totem. */
    RELIES_ON_TOTEM(PatternGroup.RESOURCES, 0.50),
    /** Keeps its distance and shoots. */
    KITES_AT_RANGE(PatternGroup.DISTANCE, 0.60),
    /** Climbs out of melee reach. */
    SEEKS_HIGH_GROUND(PatternGroup.DISTANCE, 0.60),
    /** Dives from above with a mace. */
    MACE_DIVER(PatternGroup.DISTANCE, 0.50),
    /** An entity hits on a fixed interval. No threshold and no phrase. */
    FIXED_CADENCE(PatternGroup.RHYTHM, Double.NaN);

    private final PatternGroup group;
    private final double theta;

    EnemyPattern(PatternGroup group, double theta) {
        this.group = group;
        this.theta = theta;
    }

    /**
     * Family of this pattern.
     *
     * @return the group
     */
    public PatternGroup group() {
        return group;
    }

    /**
     * Activation threshold. {@link #FIXED_CADENCE} has none.
     *
     * @return theta, or {@code NaN} when the pattern is not a Beta detector
     */
    public double theta() {
        return theta;
    }

    /**
     * Whether this pattern uses the Beta detector.
     *
     * @return {@code false} only for {@link #FIXED_CADENCE}
     */
    public boolean usesBeta() {
        return this != FIXED_CADENCE;
    }

    /**
     * Bit of this pattern in a 32-bit mask.
     *
     * @return {@code 1 << ordinal}
     */
    public int bit() {
        return 1 << ordinal();
    }
}
