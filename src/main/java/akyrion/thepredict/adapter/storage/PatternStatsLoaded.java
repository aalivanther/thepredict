package akyrion.thepredict.adapter.storage;

/**
 * One loaded row of {@code m26_pattern_stats} (M26.9). It is never read on the tick.
 *
 * @param profile profile
 * @param pattern pattern name
 * @param targetKind {@code player} or {@code entity}
 * @param mode mode that was in force
 * @param encounters encounters
 * @param activations activations
 * @param exploitedCasts motivated casts
 * @param exploitedLanded motivated casts that landed
 * @param counterWindows windows
 * @param counterEffectMean Chan mean
 * @param counterEffectM2 Chan second moment
 * @param firstEpochMs first window
 * @param lastEpochMs last window
 */
public record PatternStatsLoaded(String profile, String pattern, String targetKind, String mode, long encounters,
                                 long activations, long exploitedCasts, long exploitedLanded, long counterWindows,
                                 double counterEffectMean, double counterEffectM2, long firstEpochMs,
                                 long lastEpochMs) {
}
