package akyrion.thepredict.domain.learn;

import java.util.Objects;

/**
 * One fused update of {@code m28_value_stats} (M28.1). Means use Chan's formula.
 *
 * @param key the row
 * @param windows windows in this delta
 * @param rewardMean mean reward
 * @param rewardM2 second moment
 * @param effectSum sum of immediate effects
 * @param creditSum sum of deferred credits
 * @param avoidedSum sum of avoided damage
 * @param jevSeen windows that asked Jev
 * @param jevAgree of those, how often Jev matched the best learned candidate
 * @param epochMs wall time, stored by the adapter, never hashed into an artifact body
 */
public record ValueStatDelta(ValueStatKey key, int windows, double rewardMean, double rewardM2, double effectSum,
                             double creditSum, double avoidedSum, int jevSeen, int jevAgree, long epochMs) {
    /**
     * Validates the delta.
     */
    public ValueStatDelta {
        Objects.requireNonNull(key, "key");
        if (windows < 0 || jevSeen < 0 || jevAgree < 0 || jevAgree > jevSeen) {
            throw new IllegalArgumentException("value counts are inconsistent");
        }
    }
}
