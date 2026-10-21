package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * How the reach of a skill is measured, so ThePredict measures it as MythicMobs does (M19.3, 01 §3). A component of
 * {@link SkillPurpose}; it never reaches Jev, whose reach phrases only use the band of {@code reachBlocks}, so
 * {@link SkillPurpose#semanticallyEqual} ignores it.
 */
public enum ReachMeasure {
    /** Gap between the bounding boxes, inside when {@code gap <= r}: {@code MELEE} and {@code CONE} as in M15.6. */
    BOX_GAP,
    /** Distance between the centres of the boxes, inside when {@code d <= r}: the ranged deliveries as in M15.6. */
    CENTRES,
    /**
     * Distance between the positions ({@code getLocation()}, the feet), inside when {@code d < r}: the radius of an
     * {@code AREA_AROUND_SELF}, as the MythicMobs radius targeters measure it, and every reach read from
     * {@code targetwithin}, as {@code TargetWithinCondition} measures it.
     */
    POSITIONS_STRICT,
    /** Distance between the positions, inside when {@code d <= r}: the {@code range} of {@code @EntitiesInCone}. */
    POSITIONS;

    /**
     * The measure a purpose uses unless it was read from the skill: {@code BOX_GAP} for {@code MELEE} and
     * {@code CONE}, {@code POSITIONS_STRICT} for {@code AREA_AROUND_SELF}, {@code CENTRES} for the others
     * ({@code SELF} keeps it unused).
     *
     * @param delivery a delivery
     * @return its default measure
     */
    public static ReachMeasure defaultFor(Delivery delivery) {
        return switch (Objects.requireNonNull(delivery, "delivery")) {
            case MELEE, CONE -> BOX_GAP;
            case AREA_AROUND_SELF -> POSITIONS_STRICT;
            case PROJECTILE, HITSCAN, AREA_AT_TARGET, SELF -> CENTRES;
        };
    }

    /**
     * Tells whether the measure reads the distance between the entity positions.
     *
     * @return {@code true} for {@code POSITIONS_STRICT} and {@code POSITIONS}
     */
    public boolean betweenPositions() {
        return this == POSITIONS_STRICT || this == POSITIONS;
    }
}
