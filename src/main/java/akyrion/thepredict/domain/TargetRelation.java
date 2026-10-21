package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * How a mob perceives its current target. Immutable.
 *
 * @param target alias of the target
 * @param distance distance band
 * @param trend how the distance changes
 * @param reachable whether a path reaches the target
 * @param visible whether the mob sees the target
 * @param outsideTargetReach whether the mob stands outside the target's melee reach
 * @param flankable whether the target can be flanked
 * @param grouped whether another perceived participant stands within {@value #GROUP_RADIUS_BLOCKS} blocks of the
 *        target (M15.5)
 */
public record TargetRelation(ParticipantAlias target, DistanceBand distance, Trend trend,
                             boolean reachable, boolean visible, boolean outsideTargetReach,
                             boolean flankable, boolean grouped) {
    /** Largest distance between the centres of the target and another participant that counts as grouped. */
    public static final double GROUP_RADIUS_BLOCKS = 4.0;

    /**
     * Validates the relation.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public TargetRelation {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(distance, "distance");
        Objects.requireNonNull(trend, "trend");
    }
}
