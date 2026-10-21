package akyrion.thepredict.domain;

import java.util.Optional;

/**
 * Closed counter of each pattern (M26.4), table v1. A missing choice does nothing. No counter shortens a tell,
 * a floor, slack, a wind-up or a cooldown. {@code KNOCKBACK} is the displaces-target bit: {@link SkillRole} has
 * no knockback role.
 */
public final class CounterMap {
    /** Table version. A change is a {@code CODE_PRESET}. */
    public static final int VERSION = 1;

    private static final int LIFT = 100;
    private static final Bias[] BIAS = biases();

    private CounterMap() {
    }

    /**
     * The bias of one pattern. Cadence has none.
     *
     * @param pattern the pattern
     * @return the bias
     */
    public static Bias of(EnemyPattern pattern) {
        return BIAS[pattern.ordinal()];
    }

    private static Bias[] biases() {
        var table = new Bias[EnemyPattern.values().length];
        rhythmMovementAndDefense(table, LIFT);
        tells(table, LIFT);
        resourcesAndDistance(table, LIFT);
        return table;
    }

    /** Rows 0–9: rhythm, movement and defense (M26.4). {@code NO_SHIELD} has no counter. */
    private static void rhythmMovementAndDefense(Bias[] table, int lift) {
        table[0] = new Bias(CastPreference.damage(DamageTier.MEDIUM), MicroBehaviour.ENTER, lift, false,
                Optional.empty(), 0);
        table[1] = new Bias(CastPreference.role(SkillRole.DAMAGE) | CastPreference.role(SkillRole.CONTROL),
                MicroBehaviour.BACKSTEP, lift, false, Optional.empty(), 0);
        table[2] = new Bias(CastPreference.role(SkillRole.CONTROL) | CastPreference.displacesTarget(),
                MicroBehaviour.SIDESTEP, lift, false, Optional.empty(), 0);
        table[3] = new Bias(CastPreference.delivery(Delivery.AREA_AT_TARGET) | CastPreference.delivery(Delivery.CONE),
                MicroBehaviour.ORBIT, lift, false, Optional.empty(), 0);
        table[4] = table[3];
        table[5] = new Bias(CastPreference.delivery(Delivery.AREA_AT_TARGET) | CastPreference.role(SkillRole.CONTROL),
                MicroBehaviour.BACKSTEP, lift, false, Optional.empty(), 0);
        table[6] = new Bias(CastPreference.ignoresShield(), MicroBehaviour.ORBIT, lift, false, Optional.empty(), 0);
        table[7] = new Bias(CastPreference.answer(TellAnswer.BLOCK), MicroBehaviour.NATIVE, lift, false,
                Optional.empty(), 0);
        table[8] = new Bias(CastPreference.role(SkillRole.INTERRUPT) | CastPreference.damage(DamageTier.MEDIUM),
                MicroBehaviour.BACKSTEP, lift, false, Optional.empty(), 0);
        table[9] = new Bias(0L, MicroBehaviour.NATIVE, 0, false, Optional.empty(), 0);
    }

    /** Rows 10–16: the tells, one failed answer per {@link TellAnswer} in its order, then projectiles and areas. */
    private static void tells(Bias[] table, int lift) {
        var answers = TellAnswer.values();
        for (var answer = 0; answer < answers.length; answer++) {
            table[10 + answer] = new Bias(CastPreference.answer(answers[answer]), MicroBehaviour.NATIVE, lift, false,
                    Optional.empty(), 0);
        }
        table[15] = new Bias(CastPreference.delivery(Delivery.PROJECTILE), MicroBehaviour.NATIVE, lift, false,
                Optional.empty(), 0);
        table[16] = new Bias(CastPreference.delivery(Delivery.AREA_AT_TARGET) | CastPreference.delivery(Delivery.CONE),
                MicroBehaviour.NATIVE, lift, false, Optional.empty(), 0);
    }

    /** Rows 17–24: resources and distance. {@code FIXED_CADENCE} has no counter. */
    private static void resourcesAndDistance(Bias[] table, int lift) {
        table[17] = new Bias(CastPreference.role(SkillRole.INTERRUPT), MicroBehaviour.NATIVE, lift, true,
                Optional.of(GroupPlan.PRESS_MAIN_TARGET), 0);
        table[18] = new Bias(CastPreference.delivery(Delivery.PROJECTILE) | CastPreference.role(SkillRole.GAP_CLOSE),
                MicroBehaviour.NATIVE, lift, true, Optional.empty(), 20);
        table[19] = new Bias(CastPreference.role(SkillRole.GAP_CLOSE) | CastPreference.delivery(Delivery.PROJECTILE),
                MicroBehaviour.NATIVE, lift, false, Optional.of(GroupPlan.HARASS_FROM_RANGE), 0);
        table[20] = new Bias(CastPreference.role(SkillRole.FINISHER), MicroBehaviour.NATIVE, lift, true,
                Optional.empty(), 0);
        table[21] = new Bias(CastPreference.role(SkillRole.GAP_CLOSE), MicroBehaviour.NATIVE, lift, false,
                Optional.of(GroupPlan.SURROUND_TARGET), 0);
        table[22] = new Bias(CastPreference.delivery(Delivery.PROJECTILE), MicroBehaviour.STILL, lift, false,
                Optional.of(GroupPlan.HARASS_FROM_RANGE), 0);
        table[23] = new Bias(CastPreference.damage(DamageTier.MEDIUM), MicroBehaviour.SIDESTEP, lift, false,
                Optional.empty(), 0);
        table[24] = new Bias(0L, MicroBehaviour.NATIVE, 0, false, Optional.empty(), 0);
    }

    /**
     * One row of the counter table.
     *
     * @param preference bits written into the opportunity slot
     * @param micro micro the tick layer may publish
     * @param utilityMillis added thousandths, so the factor stays in {@code [1, 1.2]}
     * @param reserve whether a ready choice is held
     * @param plan group plan hinted by this pattern
     * @param delayTicks delay of a melee wind-up, never above 20
     */
    public record Bias(long preference, MicroBehaviour micro, int utilityMillis, boolean reserve,
                       Optional<GroupPlan> plan, int delayTicks) {
        /**
         * Checks the legal ranges.
         *
         * @throws IllegalArgumentException when the factor or the delay would break A14
         */
        public Bias {
            if (utilityMillis < 0 || utilityMillis > 200) {
                throw new IllegalArgumentException("utility factor must stay within [1, 1.2]");
            }
            if (delayTicks < 0 || delayTicks > 20) {
                throw new IllegalArgumentException("counter delay must be within [0, 20]");
            }
            if (plan == null || micro == null) {
                throw new NullPointerException("counter");
            }
        }

        /**
         * Utility factor. Never below one.
         *
         * @return the factor
         */
        public double factor() {
            return 1.0 + utilityMillis / 1000.0;
        }
    }
}
