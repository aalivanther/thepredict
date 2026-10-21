package akyrion.thepredict.domain;

/**
 * The global zone limits (M24). Every default lives here and only here (rule C13).
 *
 * @param mode delivered zone mode
 * @param defaultRadiusBlocks radius when the profile does not set one
 * @param maxRadiusBlocks greatest radius, including an arena
 * @param bandBlocks vertical half-band
 * @param maxZones encounters that may hold a map
 * @param maxSnapshotsPerTick chunk copies taken on the server thread
 * @param maxConcurrentBuilds builds queued to the zone thread
 * @param maxDropScanBlocks how far a drop is followed
 * @param maxMicrosPerTick budget of the zone step
 * @param maxColumnReadsPerTick live column reads
 * @param minColumnRefreshTicks gap between two reads of one column
 * @param maxDirtyQueue columns waiting for a patch
 * @param dirtyRebuildThreshold dirty columns that force a rebuild
 * @param verifyIntervalTicks ticks between a full check on a hot tier
 * @param idleDropTicks ticks before an idle map is dropped
 * @param maxQueriesPerTick queries served in one tick
 * @param knockbackDepthBlocks steps of a knockback
 * @param strongPushDepthBlocks steps of a strong push
 * @param maxSafeDropBlocks drop that is still not an edge
 * @param factPeriodTicks ticks between fact samples
 * @param factOnTicks raw hits before a fact enters
 * @param factOffTicks raw misses before a fact leaves
 * @param maxFactsPerMob facts sent for one mob
 * @param pushDeferTicks ticks of one deferral
 * @param pushDeferMaxTicks ticks before a deferred push is skipped
 * @param pushAttributionTicks ticks a push stays attributable
 * @param answerRetryTicks ticks between infeasible retries
 * @param answerMaxWaitTicks ticks before an infeasible tell is dropped
 */
public record ZoneCaps(ZoneMode mode, int defaultRadiusBlocks, int maxRadiusBlocks, int bandBlocks, int maxZones,
                       int maxSnapshotsPerTick, int maxConcurrentBuilds, int maxDropScanBlocks, int maxMicrosPerTick,
                       int maxColumnReadsPerTick, int minColumnRefreshTicks, int maxDirtyQueue,
                       int dirtyRebuildThreshold, int verifyIntervalTicks, int idleDropTicks, int maxQueriesPerTick,
                       int knockbackDepthBlocks, int strongPushDepthBlocks, int maxSafeDropBlocks, int factPeriodTicks,
                       int factOnTicks, int factOffTicks, int maxFactsPerMob, int pushDeferTicks, int pushDeferMaxTicks,
                       int pushAttributionTicks, int answerRetryTicks, int answerMaxWaitTicks) {
    /** The delivered limits. The mode is observe. */
    public static final ZoneCaps DEFAULT = new ZoneCaps(ZoneMode.OBSERVE, 24, 48, 12, 64, 4, 2, 64, 250, 16, 4, 256,
            64, 200, 600, 32, 2, 7, 3, 10, 10, 40, 3, 10, 60, 60, 4, 40);

    /**
     * Checks the ranges and the cross checks.
     *
     * @throws IllegalArgumentException when a limit is outside its range
     */
    public ZoneCaps {
        if (mode == null) {
            throw new IllegalArgumentException("zone.mode is required");
        }
        range(defaultRadiusBlocks, 8, maxRadiusBlocks, "zone.defaultRadiusBlocks");
        range(maxRadiusBlocks, 16, 64, "zone.maxRadiusBlocks");
        range(bandBlocks, 6, 24, "zone.bandBlocks");
        range(maxZones, 1, 256, "zone.maxZones");
        range(maxSnapshotsPerTick, 1, 32, "zone.maxSnapshotsPerTick");
        range(maxConcurrentBuilds, 1, 8, "zone.maxConcurrentBuilds");
        range(maxDropScanBlocks, 8, 384, "zone.maxDropScanBlocks");
        range(maxMicrosPerTick, 50, 2000, "zone.maxMicrosPerTick");
        range(maxColumnReadsPerTick, 0, 256, "zone.maxColumnReadsPerTick");
        range(minColumnRefreshTicks, 1, 40, "zone.minColumnRefreshTicks");
        range(maxDirtyQueue, 16, 4096, "zone.maxDirtyQueue");
        range(dirtyRebuildThreshold, 8, maxDirtyQueue, "zone.dirtyRebuildThreshold");
        range(verifyIntervalTicks, 40, 2400, "zone.verifyIntervalTicks");
        range(idleDropTicks, 100, 6000, "zone.idleDropTicks");
        range(maxQueriesPerTick, 4, 256, "zone.maxQueriesPerTick");
        range(knockbackDepthBlocks, 1, 6, "zone.knockbackDepthBlocks");
        range(strongPushDepthBlocks, knockbackDepthBlocks, 16, "zone.strongPushDepthBlocks");
        range(maxSafeDropBlocks, 0, 8, "zone.maxSafeDropBlocks");
        range(factPeriodTicks, 2, 40, "zone.factPeriodTicks");
        range(factOnTicks, 2, 100, "zone.factOnTicks");
        range(factOffTicks, factOnTicks, 400, "zone.factOffTicks");
        range(maxFactsPerMob, 0, 5, "zone.maxFactsPerMob");
        range(pushDeferTicks, 2, 40, "zone.pushDeferTicks");
        range(pushDeferMaxTicks, pushDeferTicks, 400, "zone.pushDeferMaxTicks");
        range(pushAttributionTicks, 20, 200, "zone.pushAttributionTicks");
        range(answerRetryTicks, 1, 20, "zone.answerRetryTicks");
        range(answerMaxWaitTicks, answerRetryTicks, 200, "zone.answerMaxWaitTicks");
    }

    /**
     * The radius of an arena anchor: the arena radius plus four, never above the maximum.
     *
     * @param arenaRadius authored arena radius
     * @return the zone radius
     */
    public int arenaRadius(int arenaRadius) {
        var widened = arenaRadius + 4;
        return widened > maxRadiusBlocks ? maxRadiusBlocks : widened;
    }

    private static void range(int value, int least, int most, String path) {
        if (value < least || value > most) {
            throw new IllegalArgumentException(path + " must be within [" + least + ", " + most + "], was " + value);
        }
    }
}
