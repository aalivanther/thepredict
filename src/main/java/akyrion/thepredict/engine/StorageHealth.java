package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * A cheap snapshot of the store (M21.6). Read on any thread; O(1).
 *
 * @param state the state
 * @param cause short cause, empty when healthy
 * @param droppedEvents events the writer discarded
 * @param batchesInFlight batches waiting for a reply
 * @param failedBatches batches that failed
 */
public record StorageHealth(StorageState state, String cause, long droppedEvents, int batchesInFlight,
                            long failedBatches) {
    /** Aggregates off. */
    public static final StorageHealth DISABLED = new StorageHealth(StorageState.DISABLED, "", 0, 0, 0);

    /**
     * Validates the snapshot.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a count is negative
     */
    public StorageHealth {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(cause, "cause");
        if (droppedEvents < 0 || batchesInFlight < 0 || failedBatches < 0) {
            throw new IllegalArgumentException("invalid storage health");
        }
    }
}
