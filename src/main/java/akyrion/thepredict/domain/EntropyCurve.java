package akyrion.thepredict.domain;

/**
 * Entropy reaction and adaptation curves (M22.13, errata E22-4). Players only. The rung stays at the maximum and no
 * stat changes. {@code r(t) = max(entropyFloor, round(r0 · (1 − t/T)))}. {@code c(t) = c0 · max(0, 1 − t/T)}.
 */
public final class EntropyCurve {
    private EntropyCurve() {
    }

    /**
     * Reaction delay at an instant, in ticks.
     *
     * @param normalFloorTicks {@code r0}, the preset floor
     * @param entropyFloorTicks the entropy floor
     * @param elapsedSeconds seconds since the fight engaged
     * @param targetSeconds T, in seconds
     * @return {@code r(t)}
     */
    public static int reactionTicks(int normalFloorTicks, int entropyFloorTicks, double elapsedSeconds,
                                    double targetSeconds) {
        var ratio = targetSeconds > 0.0 ? elapsedSeconds / targetSeconds : 1.0;
        var clamped = Math.min(1.0, Math.max(0.0, ratio));
        var rounded = (int) StrictMath.round(normalFloorTicks * (1.0 - clamped));
        return Math.max(entropyFloorTicks, rounded);
    }

    /**
     * Adaptation seconds at an instant.
     *
     * @param startSeconds {@code c0}
     * @param elapsedSeconds seconds since the fight engaged
     * @param targetSeconds T, in seconds
     * @return {@code c(t)}
     */
    public static double adaptSeconds(double startSeconds, double elapsedSeconds, double targetSeconds) {
        var ratio = targetSeconds > 0.0 ? elapsedSeconds / targetSeconds : 1.0;
        return startSeconds * Math.max(0.0, 1.0 - Math.min(1.0, ratio));
    }

    /**
     * The interval actually used: the smaller of {@code c(t)} converted to ticks and the normal interval. A normal
     * interval of zero or less does not cap {@code c(t)}.
     *
     * @param adaptSeconds {@code c(t)}
     * @param normalIntervalTicks the profile interval
     * @return the effective interval in ticks, {@code >= 0}; {@code 0} means no wait, which the curve reaches with the
     *         {@code VACIO} preset and at {@code t >= T}, and the callers then bound the pace by the request in flight
     *         and the budget
     */
    public static int effectiveIntervalTicks(double adaptSeconds, int normalIntervalTicks) {
        var ticks = (int) StrictMath.round(adaptSeconds * 20.0);
        if (normalIntervalTicks <= 0) {
            return Math.max(0, ticks);
        }
        return Math.min(normalIntervalTicks, Math.max(0, ticks));
    }
}
