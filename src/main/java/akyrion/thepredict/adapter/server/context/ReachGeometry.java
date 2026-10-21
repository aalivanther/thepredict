package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import org.bukkit.util.BoundingBox;

import java.util.Objects;

/**
 * Reach geometry of a skill by {@link akyrion.thepredict.domain.ReachMeasure} (M15.5, M15.6, M19.3), shared by the
 * capture, which marks the choices out of reach, and the pre-cast check of M10.3, so both use the same rule.
 * Stateless and pure; safe on any thread. An untyped choice is never passed here.
 *
 * <p>{@code BOX_GAP} compares the gap between the bounding boxes ({@code gap <= r}); {@code CENTRES} the distance
 * between the centres ({@code d <= r}); {@code POSITIONS_STRICT} the distance between the entity positions
 * ({@code getLocation()}, the feet, {@code d < r}); {@code POSITIONS} the same distance inclusively ({@code d <= r}).
 * A purpose without a reach limit is always within reach. Evidence (javap, MythicMobs Premium 5.13.1):
 * {@code PlayersInRadiusTargeter.getEntities} keeps a player when
 * {@code IEntitySelector.distanceSquared(caster.getLocation(), player) < r * r}; {@code EntitiesInCone} keeps
 * {@code <= range * range}.
 */
public final class ReachGeometry {
    private ReachGeometry() {
    }

    /**
     * Tells whether a skill reaches its target now.
     *
     * @param purpose purpose of the skill
     * @param caster bounding box of the caster
     * @param target bounding box of the target
     * @param positionDistanceSquared squared distance between the positions of the caster and the target
     *        ({@code Entity#getLocation()}), in square blocks; only {@code POSITIONS_STRICT} and {@code POSITIONS}
     *        read it
     * @return whether the target is within the reach of the skill
     */
    public static boolean withinReach(SkillPurpose purpose, BoundingBox caster, BoundingBox target,
                                      double positionDistanceSquared) {
        Objects.requireNonNull(purpose, "purpose");
        var reach = purpose.effectiveReachBlocks();
        if (reach.isEmpty()) {
            return true;
        }
        var limit = reach.getAsDouble();
        return switch (purpose.reachMeasure()) {
            case BOX_GAP -> gapBlocks(caster, target) <= limit;
            case CENTRES -> centreDistanceSquared(caster, target) <= limit * limit;
            case POSITIONS_STRICT -> positionDistanceSquared < limit * limit;
            case POSITIONS -> positionDistanceSquared <= limit * limit;
        };
    }

    /**
     * How far a rival mob bound to ThePredict can hit, from its profile (M17.4): the largest reach of its damaging
     * choices whose purpose the author wrote (M15), or its melee reach when it has none. It is compared with the gap
     * between the bounding boxes, like the vanilla melee reach of a mob.
     *
     * @param profile profile of the rival
     * @return reach in blocks
     */
    public static double profileReachBlocks(TacticalProfile profile) {
        var reach = 0.0;
        for (var choice : profile.choices().values()) {
            var purpose = choice.purpose();
            if (purpose.isEmpty() || !purpose.get().authored()) {
                continue;
            }
            var found = purpose.get();
            var damaging = found.roles().contains(SkillRole.DAMAGE) || found.roles().contains(SkillRole.FINISHER);
            var declared = found.effectiveReachBlocks();
            if (damaging && declared.isPresent()) {
                reach = Math.max(reach, declared.getAsDouble());
            }
        }
        return reach > 0.0 ? reach : profile.movement().meleeReachBlocks();
    }

    /**
     * The test of a MythicMobs radius targeter such as {@code @PlayersInRadius{r=...}}: the position of the target is
     * strictly closer than the radius to the position of the caster.
     *
     * @param positionDistanceSquared squared distance between the two positions, in square blocks
     * @param radiusBlocks radius of the targeter, {@code r}
     * @return whether the targeter reaches the target
     */
    public static boolean withinRadius(double positionDistanceSquared, double radiusBlocks) {
        return positionDistanceSquared < radiusBlocks * radiusBlocks;
    }

    /**
     * Tells whether a delivery measures its reach as the gap between bounding boxes.
     *
     * @param delivery delivery of the skill
     * @return {@code true} for {@code MELEE} and {@code CONE}
     */
    public static boolean measuresGap(Delivery delivery) {
        return delivery == Delivery.MELEE || delivery == Delivery.CONE;
    }

    /**
     * Distance between two bounding boxes: 0 when they touch or overlap.
     *
     * @param first first box
     * @param second second box
     * @return shortest distance in blocks
     */
    public static double gapBlocks(BoundingBox first, BoundingBox second) {
        var x = axisGap(first.getMinX(), first.getMaxX(), second.getMinX(), second.getMaxX());
        var y = axisGap(first.getMinY(), first.getMaxY(), second.getMinY(), second.getMaxY());
        var z = axisGap(first.getMinZ(), first.getMaxZ(), second.getMinZ(), second.getMaxZ());
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Squared distance between the centres of two bounding boxes, without allocating.
     *
     * @param first first box
     * @param second second box
     * @return squared distance in blocks
     */
    public static double centreDistanceSquared(BoundingBox first, BoundingBox second) {
        var x = first.getCenterX() - second.getCenterX();
        var y = first.getCenterY() - second.getCenterY();
        var z = first.getCenterZ() - second.getCenterZ();
        return x * x + y * y + z * z;
    }

    private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0, Math.max(firstMin - secondMax, secondMin - firstMax));
    }
}
