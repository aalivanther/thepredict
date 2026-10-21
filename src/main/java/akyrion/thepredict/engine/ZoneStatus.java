package akyrion.thepredict.engine;

/**
 * Whether a zone may be read (M24.2).
 *
 * @param ready whether the published grid may answer queries
 * @param epoch the build epoch
 * @param snapshotTick the tick of the snapshot that produced the grid
 */
public record ZoneStatus(boolean ready, int epoch, long snapshotTick) {
}
