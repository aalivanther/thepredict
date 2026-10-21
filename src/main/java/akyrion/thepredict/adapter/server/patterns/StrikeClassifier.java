package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.EnemyAction;

/**
 * Classifies a hit that landed on a member (M26.1). Charge is {@code getAttackCooldown()} before the reset, and
 * critical is {@code isCritical()} of the same tick. A spear is only {@link EnemyAction#STRIKE} or
 * {@link EnemyAction#STRIKE_EARLY}.
 */
public final class StrikeClassifier {
    /** Charge that counts as a full attack. */
    public static final double FULL_CHARGE = 0.9;

    private StrikeClassifier() {
    }

    /**
     * The action of one landed hit.
     *
     * @param critical {@code isCritical()}
     * @param sprinting the attacker is sprinting
     * @param charge attack cooldown before the reset
     * @param spear the weapon is a spear
     * @return the strike token
     */
    public static EnemyAction classify(boolean critical, boolean sprinting, double charge, boolean spear) {
        if (!spear && critical) {
            return EnemyAction.CRIT_STRIKE;
        }
        if (charge > FULL_CHARGE) {
            return !spear && sprinting ? EnemyAction.SPRINT_STRIKE : EnemyAction.STRIKE;
        }
        return EnemyAction.STRIKE_EARLY;
    }
}
