package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Whether the controlled movement of a mob makes progress. Immutable.
 *
 * @param movementGoalRunning whether a ThePredict movement goal is running
 * @param hasPath whether the mob has a path
 * @param pathReachesTarget whether the path ends at the target; requires {@code hasPath}
 * @param targetDistanceBlocks distance to the target, if known; finite and non-negative
 */
public record MovementProgress(boolean movementGoalRunning, boolean hasPath,
                               boolean pathReachesTarget, OptionalDouble targetDistanceBlocks) {
    /**
     * Validates the progress.
     *
     * @throws NullPointerException if {@code targetDistanceBlocks} is {@code null}
     * @throws IllegalArgumentException if the path reaches the target without a path, or the distance is invalid
     */
    public MovementProgress {
        Objects.requireNonNull(targetDistanceBlocks, "targetDistanceBlocks");
        if (pathReachesTarget && !hasPath) {
            throw new IllegalArgumentException("pathReachesTarget requires hasPath");
        }
        if (targetDistanceBlocks.isPresent()) {
            DomainChecks.requireNonNegative(targetDistanceBlocks.getAsDouble(), "targetDistanceBlocks");
        }
    }
}
