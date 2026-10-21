package akyrion.thepredict.engine;

/** Health of the aggregate store (M21.6). */
public enum StorageState {
    /** Aggregates are off, or the driver is missing or too old. */
    DISABLED,
    /** The writer has not finished opening. */
    STARTING,
    /** Aggregates accept batches. The cache stays {@code WARMING} until the first load. */
    READY,
    /** The file is newer, altered, corrupt or the disk is full. Nothing is written. */
    READ_ONLY,
    /** The in-flight ring has been full for at least 60 s. */
    DEGRADED,
    /** The writer stopped after repeated failures. */
    FAILED
}
