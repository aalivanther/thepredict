package akyrion.thepredict.engine;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One subject row of a batch (M27.3). {@code delete} removes the row after any in-flight apply.
 *
 * @param subject player id, the row key
 * @param record codec bytes, empty when deleting
 * @param fights exact fight count
 * @param updatedEpochMs wall time
 * @param delete whether the row is removed
 * @param fold digests to merge, empty when {@code record} is already encoded
 */
public record PlayerMemoryDelta(UUID subject, byte[] record, long fights, long updatedEpochMs, boolean delete,
                                Optional<MemoryFold> fold) {
    /**
     * A raw blob or a delete, with no digests to merge.
     *
     * @param subject player id
     * @param record codec bytes, empty when deleting
     * @param fights exact fight count
     * @param updatedEpochMs wall time
     * @param delete whether the row is removed
     */
    public PlayerMemoryDelta(UUID subject, byte[] record, long fights, long updatedEpochMs, boolean delete) {
        this(subject, record, fights, updatedEpochMs, delete, Optional.empty());
    }

    /**
     * Copies the record and rejects a blob past the codec cap.
     *
     * @throws IllegalArgumentException if the record is longer than 1024 bytes
     */
    public PlayerMemoryDelta {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(fold, "fold");
        record = Arrays.copyOf(record, record.length);
        if (record.length > 1024) {
            throw new IllegalArgumentException("record");
        }
    }
}
