package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Constants of A13 (M25). They are not configuration keys. A key may only be stricter.
 */
public final class GroupFairnessRules {
    /** Least impact gap, in ticks. */
    public static final int MIN_IMPACT_GAP_TICKS = 4;
    /** Least spacing around a big impact, in ticks. */
    public static final int MIN_BIG_IMPACT_SPACING_TICKS = 8;
    /** Least exit sector, in degrees. */
    public static final int MIN_EXIT_SECTOR_DEGREES = 60;
    /** Weight units of one melee token. */
    public static final int MELEE_WEIGHT = 2;
    /** Weight units of one ranged token. */
    public static final int RANGED_WEIGHT = 2;
    /** Reserved impacts kept per player. Errata E25-2. */
    public static final int IMPACT_RING = 16;
    /** Big tells kept per player. */
    public static final int TELL_RING = 4;
    /** Finite infeasible assignment cost. Not infinity. */
    public static final int INFEASIBLE = 1_000_000;
    /** Sentinel inside Hungarian. Not {@link Integer#MAX_VALUE}. */
    public static final int SENTINEL = 1_000_000_000;
    /** Cost of a reserve column at distance zero. */
    public static final int RESERVE_COST = 900;
    /** Cost of a dummy slot column. */
    public static final int NO_SLOT_COST = 100_000;

    private GroupFairnessRules() {
    }

    /**
     * Pool an untyped damaging choice asks for. A non-damaging choice asks for none. Vanilla melee is melee.
     *
     * @param damaging whether the choice deals damage, including untyped
     * @param vanillaMelee whether the choice is vanilla melee
     * @return the pool, or empty when the choice asks for none
     */
    public static Optional<AttackPool> pool(boolean damaging, boolean vanillaMelee) {
        if (!damaging) {
            return Optional.empty();
        }
        return Optional.of(vanillaMelee ? AttackPool.MELEE : AttackPool.RANGED);
    }

    /**
     * Pool a typed damaging choice asks for. A non-damaging choice asks for none.
     *
     * @param damaging whether the choice deals damage
     * @param delivery delivery of the purpose
     * @param vanillaMelee whether the choice is vanilla melee
     * @return the pool, or empty when the choice asks for none
     * @throws NullPointerException if {@code delivery} is null
     */
    public static Optional<AttackPool> pool(boolean damaging, Delivery delivery, boolean vanillaMelee) {
        Objects.requireNonNull(delivery, "delivery");
        if (!damaging) {
            return Optional.empty();
        }
        if (vanillaMelee || delivery == Delivery.MELEE || delivery == Delivery.CONE
                || delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.SELF) {
            return Optional.of(AttackPool.MELEE);
        }
        if (delivery == Delivery.PROJECTILE || delivery == Delivery.HITSCAN || delivery == Delivery.AREA_AT_TARGET) {
            return Optional.of(AttackPool.RANGED);
        }
        return Optional.empty();
    }
}
