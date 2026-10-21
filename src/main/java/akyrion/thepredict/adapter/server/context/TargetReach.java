package akyrion.thepredict.adapter.server.context;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.Objects;

/**
 * How far a target can hit the mob, for {@code TargetRelation.outsideTargetReach} (M17.4, DT-37). Four kinds, each
 * compared the way its source measures it:
 * <ul>
 * <li>{@link Kind#DISTANCE}: a player, whose reach is compared with the distance between positions, as in 0.0.1;</li>
 * <li>{@link Kind#BOX_GAP}: a rival bound to ThePredict, whose reach comes from its profile (M15) and is compared with
 * the gap between the bounding boxes;</li>
 * <li>{@link Kind#VANILLA_MELEE}: a vanilla {@code Mob}, checked exactly as {@code Mob.isWithinMeleeAttackRange} of the
 * patched Paper 26.2 server (javap): the attacker's box, widened on {@code x} and {@code z} to its vehicle's when it
 * rides one, is inflated by the reach on {@code x} and {@code z} only ({@code inflate(r, 0, r)}) and must intersect
 * the victim's box on every axis; the reach is {@code ATTACK_RANGE.maxReach() × mobFactor()} of the attacker's weapon
 * or {@code Mob.DEFAULT_ATTACK_REACH}, and with a minimum reach above 0 a victim inside the box inflated by the
 * minimum is out of reach too;</li>
 * <li>{@link Kind#UNKNOWN}: anything else, which never counts as out of reach (rule A6: Jev receives less, never a
 * false fact).</li>
 * </ul>
 * The victim's box is the mob's bounding box. Vanilla raises its bottom to the riding position when the mob rides a
 * vehicle; the whole box is kept, which can only make the target look within reach, never out of it. Immutable.
 *
 * @param kind how the reach is compared
 * @param maxBlocks the reach in blocks; {@code 0} for {@code UNKNOWN}
 * @param minBlocks the minimum reach of a vanilla weapon in blocks, {@code 0} without one
 */
public record TargetReach(Kind kind, double maxBlocks, double minBlocks) {
    /**
     * Vanilla melee reach of a {@code Mob} without {@code ATTACK_RANGE}: {@code Mob.DEFAULT_ATTACK_REACH =
     * Math.sqrt(2.04f) - 0.6f}, about 0.828 blocks (javap on the patched Paper 26.2 server, DT-37).
     */
    public static final double MOB_DEFAULT_ATTACK_REACH_BLOCKS = Math.sqrt(2.04f) - 0.6f;
    /** Nothing is known: never out of reach. */
    public static final TargetReach UNKNOWN = new TargetReach(Kind.UNKNOWN, 0.0, 0.0);

    /** How a reach is compared. */
    public enum Kind {
        /** Nothing is known. */
        UNKNOWN,
        /** Compared with the distance between positions (a player). */
        DISTANCE,
        /** Compared with the gap between the bounding boxes (a rival bound to ThePredict). */
        BOX_GAP,
        /** The vanilla melee check of a {@code Mob}. */
        VANILLA_MELEE
    }

    /**
     * Validates the reach.
     *
     * @throws NullPointerException if {@code kind} is {@code null}
     * @throws IllegalArgumentException if a value is negative or not finite, or {@code minBlocks} exceeds
     *                                  {@code maxBlocks}
     */
    public TargetReach {
        Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(maxBlocks) || !Double.isFinite(minBlocks) || maxBlocks < 0.0 || minBlocks < 0.0
                || minBlocks > maxBlocks) {
            throw new IllegalArgumentException("reach must be finite with 0 <= min <= max, was " + minBlocks
                    + " and " + maxBlocks);
        }
    }

    /**
     * The reach of a player, compared with the distance: {@code ATTACK_RANGE.maxReach()} of the main hand item when it
     * has that component, otherwise {@code ENTITY_INTERACTION_RANGE}, as in 0.0.1.
     *
     * @param interactionRangeBlocks value of {@code ENTITY_INTERACTION_RANGE}
     * @param hasAttackRange whether the main hand item has {@code ATTACK_RANGE}
     * @param attackMaxReachBlocks {@code AttackRange.maxReach()}, ignored without the component
     * @return the reach
     */
    public static TargetReach player(double interactionRangeBlocks, boolean hasAttackRange,
                                     float attackMaxReachBlocks) {
        return new TargetReach(Kind.DISTANCE, hasAttackRange ? attackMaxReachBlocks : interactionRangeBlocks, 0.0);
    }

    /**
     * The reach of a rival bound to ThePredict, from its profile.
     *
     * @param reachBlocks largest reach of its damaging choices, or its melee reach
     * @return the reach
     */
    public static TargetReach profile(double reachBlocks) {
        return new TargetReach(Kind.BOX_GAP, reachBlocks, 0.0);
    }

    /**
     * The reach of a vanilla {@code Mob}, as {@code Mob.isWithinMeleeAttackRange} reads it: with {@code ATTACK_RANGE}
     * on the weapon, {@code maxReach × mobFactor} and {@code minReach × mobFactor} computed in {@code float}, as
     * {@code AttackRange.effectiveMaxRange} and {@code effectiveMinRange} do for a non-player; otherwise
     * {@link #MOB_DEFAULT_ATTACK_REACH_BLOCKS} and no minimum.
     *
     * @param hasAttackRange whether the weapon has {@code ATTACK_RANGE}
     * @param maxReach {@code AttackRange.maxReach()}
     * @param minReach {@code AttackRange.minReach()}
     * @param mobFactor {@code AttackRange.mobFactor()}
     * @return the reach; {@link #UNKNOWN} when the component gives values that are not finite, negative or inverted
     */
    public static TargetReach vanillaMelee(boolean hasAttackRange, float maxReach, float minReach, float mobFactor) {
        if (!hasAttackRange) {
            return new TargetReach(Kind.VANILLA_MELEE, MOB_DEFAULT_ATTACK_REACH_BLOCKS, 0.0);
        }
        double max = maxReach * mobFactor;
        double min = minReach * mobFactor;
        if (!Double.isFinite(max) || !Double.isFinite(min) || max < 0.0 || min < 0.0 || min > max) {
            return UNKNOWN;
        }
        return new TargetReach(Kind.VANILLA_MELEE, max, min);
    }

    /**
     * Tells whether the mob stands outside the reach of its target.
     *
     * @param mob the member mob, the victim of the check
     * @param target its target, the attacker of the check
     * @param distanceBlocks distance between their positions
     * @return {@code true} only when the reach is known and the target cannot hit the mob from where it stands
     */
    public boolean outside(LivingEntity mob, LivingEntity target, double distanceBlocks) {
        return switch (kind) {
            case UNKNOWN -> false;
            case DISTANCE -> distanceBlocks > maxBlocks;
            case BOX_GAP -> ReachGeometry.gapBlocks(mob.getBoundingBox(), target.getBoundingBox()) > maxBlocks;
            case VANILLA_MELEE -> !withinVanillaMelee(attackerBox(target), mob.getBoundingBox(), maxBlocks,
                    minBlocks);
        };
    }

    /**
     * The attacker's box of {@code Mob.getAttackBoundingBox} before inflating: its own box, widened on {@code x} and
     * {@code z} to the box of the vehicle it rides.
     *
     * @param attacker the target that would hit
     * @return the box
     */
    static BoundingBox attackerBox(LivingEntity attacker) {
        var own = attacker.getBoundingBox();
        var vehicle = attacker.getVehicle();
        if (vehicle == null) {
            return own;
        }
        var ridden = vehicle.getBoundingBox();
        return new BoundingBox(Math.min(own.getMinX(), ridden.getMinX()), own.getMinY(),
                Math.min(own.getMinZ(), ridden.getMinZ()), Math.max(own.getMaxX(), ridden.getMaxX()), own.getMaxY(),
                Math.max(own.getMaxZ(), ridden.getMaxZ()));
    }

    /**
     * The vanilla melee check: the attacker's box inflated by {@code maxBlocks} on {@code x} and {@code z} intersects
     * the victim's box, and, with {@code minBlocks} above 0, the one inflated by {@code minBlocks} does not. The
     * intersection is strict on every axis, as {@code AABB.intersects}.
     *
     * @param attacker attacker's box, {@link #attackerBox}
     * @param victim victim's box
     * @param maxBlocks reach
     * @param minBlocks minimum reach, {@code 0} without one
     * @return whether vanilla would let the attacker hit the victim
     */
    static boolean withinVanillaMelee(BoundingBox attacker, BoundingBox victim, double maxBlocks, double minBlocks) {
        return intersects(attacker, maxBlocks, victim) && (minBlocks <= 0.0 || !intersects(attacker, minBlocks,
                victim));
    }

    private static boolean intersects(BoundingBox attacker, double horizontal, BoundingBox victim) {
        return attacker.getMinX() - horizontal < victim.getMaxX() && attacker.getMaxX() + horizontal > victim.getMinX()
                && attacker.getMinY() < victim.getMaxY() && attacker.getMaxY() > victim.getMinY()
                && attacker.getMinZ() - horizontal < victim.getMaxZ()
                && attacker.getMaxZ() + horizontal > victim.getMinZ();
    }
}
