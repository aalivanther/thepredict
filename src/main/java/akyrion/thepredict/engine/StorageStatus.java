package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * The three lines of the Storage block of {@code status} (M21.9).
 *
 * @param state store state
 * @param cause short cause, empty when none
 * @param recordsLevel configured record level
 * @param droppedEvents events discarded
 * @param batchesInFlight batches in the ring
 * @param failedBatches failed batches
 * @param sqliteVersion reported SQLite version, or empty
 * @param synchronous the synchronous pragma read back, or empty
 * @param journalMode the journal mode read back, or empty
 * @param pausedSize whether today's records hit the size cap
 * @param networkWarning whether the folder looks like a network file system
 * @param recordsFailure exception class that stopped the recorder, or empty
 */
public record StorageStatus(StorageState state, String cause, String recordsLevel, long droppedEvents,
                            int batchesInFlight, long failedBatches, String sqliteVersion, String synchronous,
                            String journalMode, boolean pausedSize, boolean networkWarning, String recordsFailure) {
    /** Shown when storage was not wired. */
    public static final StorageStatus ABSENT = new StorageStatus(StorageState.DISABLED, "", "off", 0, 0, 0, "", "", "",
            false, false, "");

    /**
     * Validates the texts.
     *
     * @throws NullPointerException if a text is {@code null}
     */
    public StorageStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(recordsLevel, "recordsLevel");
        Objects.requireNonNull(sqliteVersion, "sqliteVersion");
        Objects.requireNonNull(synchronous, "synchronous");
        Objects.requireNonNull(journalMode, "journalMode");
        Objects.requireNonNull(recordsFailure, "recordsFailure");
    }
}
