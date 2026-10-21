package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillRole;

import java.util.Set;

/**
 * Reward specification {@code r1} (M28.2). Changing {@link #ZETA} is {@code r2}. Integers are thousandths where a
 * caller stores them; the formulas themselves return doubles via {@link CreditMath}.
 */
public final class RewardSpec {
    /** Token stored in {@code m28_value_stats.spec}. */
    public static final String ID = "r1";
    /** Weight of deferred credit. */
    public static final double ETA = 0.5;
    /** Weight of the group result inside {@code z}. */
    public static final double GROUP = 0.5;
    /** Per-window decay. {@code β^5 = 0.32768}. */
    public static final double BETA = 0.8;
    /** Weight of avoided damage. */
    public static final double ZETA = 1.0;
    /** Minimum fight length that may use a measured time. */
    public static final int MIN_CLOCK_TICKS = 60;

    private RewardSpec() {
    }

    /**
     * Whether a tactic earns the avoided-damage term of {@code r1}: it retreats, or its choice has the
     * {@link SkillRole#DEFENSE} or {@link SkillRole#SUSTAIN} role of M15. The tactic name is never read, so a name
     * such as {@code guard_crush} does not make an attack defensive.
     *
     * @param movement movement of the tactic
     * @param roles roles of the tactic's choice, empty when it has none
     * @return {@code true} for a defensive tactic
     */
    public static boolean defensive(Movement movement, Set<SkillRole> roles) {
        return movement == Movement.RETREAT || roles.contains(SkillRole.DEFENSE) || roles.contains(SkillRole.SUSTAIN);
    }
}
