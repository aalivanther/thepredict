package akyrion.thepredict.domain;

/**
 * How a skill reaches its target, which decides its reach and area rules (M15.1). Closed set; it reaches Jev only as
 * the fixed phrases of {@code adapter.jev} (rule I12).
 */
public enum Delivery {
    /** A hit on a target within arm's reach; needs {@code reachBlocks}, measured between bounding boxes. */
    MELEE,
    /** A projectile that travels and can miss; {@code reachBlocks} is optional. */
    PROJECTILE,
    /** An effect at a distance that lands at once; {@code reachBlocks} is optional. */
    HITSCAN,
    /**
     * An area centred on the caster; needs {@code radiusBlocks}, the {@code r} of its MythicMobs radius targeter
     * (distance between positions), and has no {@code reachBlocks}.
     */
    AREA_AROUND_SELF,
    /** An area centred on the target; needs {@code radiusBlocks}, {@code reachBlocks} is optional. */
    AREA_AT_TARGET,
    /** A sweep in front of the caster; needs {@code reachBlocks}, the length of the cone. */
    CONE,
    /** Acts only on the caster; has neither reach nor area. */
    SELF
}
