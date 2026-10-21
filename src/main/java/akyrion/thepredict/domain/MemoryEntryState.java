package akyrion.thepredict.domain;

/**
 * Where a subject's record sits relative to the cache (M27.4).
 */
public enum MemoryEntryState {
    /** A read is in flight. */
    PENDING,
    /** The record is usable. */
    LOADED,
    /** A newer codec was stored. The row is not overwritten. */
    READ_ONLY,
    /** The load failed. A retry waits a minute. */
    FAILED,
    /** No row, or the mode is off. */
    ABSENT
}
