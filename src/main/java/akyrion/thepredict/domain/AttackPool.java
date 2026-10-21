package akyrion.thepredict.domain;

/**
 * Reservation a damaging choice asks for (M25). Non-damaging choices ask for none.
 */
public enum AttackPool {
    /** Melee, cone, vanilla melee, area around the caster, and {@code ENTER}. */
    MELEE,
    /** Projectile, hitscan and area at the target. */
    RANGED
}
