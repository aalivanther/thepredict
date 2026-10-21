package akyrion.thepredict.engine;

import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;

import java.util.List;
import java.util.Objects;

/**
 * A reply the storage thread deposits on the storage lane of the {@link Inbox} (M21.6). Sealed so later modules can
 * add variants. Never discarded: the writer retries in order and stops at the first failed offer.
 */
public sealed interface StorageReply extends InboxEntry
        permits StorageReply.BatchApplied, StorageReply.BatchFailed, StorageReply.OutcomeStatsLoaded,
        StorageReply.QueryFailed, StorageReply.AdminResult, StorageReply.DifficultyStatsLoaded,
        StorageReply.VerdictsLoaded, StorageReply.SubjectMemoryLoaded, StorageReply.MemoryApplied,
        StorageReply.ValueStatsPage, StorageReply.ArtifactsLoaded, StorageReply.ChoiceStatsPage,
        StorageReply.AgreementStatsPage {

    /**
     * A batch was applied, or was already applied.
     *
     * @param batchId the batch
     * @param completedAtNanos monotonic time of the reply
     */
    record BatchApplied(long batchId, long completedAtNanos) implements StorageReply {
        /**
         * Validates the reply.
         *
         * @throws IllegalArgumentException if the id is not positive
         */
        public BatchApplied {
            if (batchId < 1) {
                throw new IllegalArgumentException("batchId must be >= 1, was " + batchId);
            }
        }
    }

    /**
     * A batch was not applied.
     *
     * @param batchId the batch
     * @param cause short cause, such as {@code READ_ONLY}
     * @param completedAtNanos monotonic time of the reply
     */
    record BatchFailed(long batchId, String cause, long completedAtNanos) implements StorageReply {
        /**
         * Validates the reply.
         *
         * @throws NullPointerException if the cause is {@code null}
         * @throws IllegalArgumentException if the id is not positive
         */
        public BatchFailed {
            Objects.requireNonNull(cause, "cause");
            if (batchId < 1) {
                throw new IllegalArgumentException("batchId must be >= 1, was " + batchId);
            }
        }
    }

    /**
     * One loaded outcome row.
     *
     * @param key the key
     * @param stat the stat
     */
    record LoadedRow(OutcomeStatKey key, OutcomeStat stat) {
        /**
         * Validates the row.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public LoadedRow {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(stat, "stat");
        }
    }

    /**
     * The startup load of {@code outcome_stats}.
     *
     * @param rows rows, at most 50 000
     * @param appliedThroughBatch batches at or below this id are already in the rows
     * @param truncated whether the cap dropped older rows
     * @param completedAtNanos monotonic time of the reply
     */
    record OutcomeStatsLoaded(List<LoadedRow> rows, long appliedThroughBatch, boolean truncated, long completedAtNanos)
            implements StorageReply {
        /**
         * Copies the rows.
         *
         * @throws NullPointerException if the list is {@code null}
         */
        public OutcomeStatsLoaded {
            rows = List.copyOf(rows);
        }
    }

    /**
     * A read failed.
     *
     * @param cause short cause
     * @param completedAtNanos monotonic time of the reply
     */
    record QueryFailed(String cause, long completedAtNanos) implements StorageReply {
        /**
         * Validates the reply.
         *
         * @throws NullPointerException if the cause is {@code null}
         */
        public QueryFailed {
            Objects.requireNonNull(cause, "cause");
        }
    }

    /**
     * An admin command finished. {@code engine} does not know {@code admin}; the watcher was registered with the command.
     *
     * @param commandId id assigned at submit
     * @param ok whether the command succeeded
     * @param detail short text for the sender
     * @param completedAtNanos monotonic time of the reply
     */
    record AdminResult(long commandId, boolean ok, String detail, long completedAtNanos) implements StorageReply {
        /**
         * Validates the reply.
         *
         * @throws NullPointerException if the detail is {@code null}
         */
        public AdminResult {
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * Difficulty rows loaded for a query (M22.6).
     *
     * @param rows the rows
     * @param completedAtNanos monotonic time of the reply
     */
    record DifficultyStatsLoaded(List<DifficultyStatDelta> rows, long completedAtNanos) implements StorageReply {
        /**
         * Copies the rows.
         */
        public DifficultyStatsLoaded {
            rows = List.copyOf(rows);
        }
    }

    /**
     * One subject's stored record (M27.4). An empty blob means no row.
     *
     * @param subject the player
     * @param record the column, empty when absent
     * @param appliedThroughBatch batches already in the row
     * @param newer whether the codec refused the row
     * @param completedAtNanos monotonic time of the reply
     */
    record SubjectMemoryLoaded(java.util.UUID subject, java.util.Optional<byte[]> record, long appliedThroughBatch,
                               boolean newer, long completedAtNanos) implements StorageReply {
        /**
         * Copies the blob.
         */
        public SubjectMemoryLoaded {
            java.util.Objects.requireNonNull(subject, "subject");
            record = record.map(byte[]::clone);
        }
    }

    /**
     * A batch that carried memory was applied. It counts as {@link BatchApplied} for the ring and replaces the cache.
     *
     * @param batchId the batch
     * @param records subject to stored bytes, absent when the row was deleted
     * @param completedAtNanos monotonic time of the reply
     */
    record MemoryApplied(long batchId, java.util.Map<java.util.UUID, byte[]> records, long completedAtNanos)
            implements StorageReply {
        /**
         * Copies the map.
         */
        public MemoryApplied {
            records = java.util.Map.copyOf(records);
            if (batchId < 1) {
                throw new IllegalArgumentException("batchId must be >= 1, was " + batchId);
            }
        }
    }

    /**
     * Gate verdicts loaded for a query (M22.4). Each row is sha256, gate, verdict, run id and epoch.
     *
     * @param rows the rows, newest first
     * @param completedAtNanos monotonic time of the reply
     */
    record VerdictsLoaded(List<GateVerdictRow> rows, long completedAtNanos) implements StorageReply {
        /**
         * Copies the rows.
         */
        public VerdictsLoaded {
            rows = List.copyOf(rows);
        }
    }

    /**
     * One stored gate verdict.
     *
     * @param sha256 candidate fingerprint
     * @param gate gate name
     * @param verdict verdict name
     * @param runId run that decided it
     * @param decidedEpochMs wall time of the decision
     */
    record GateVerdictRow(String sha256, String gate, String verdict, String runId, long decidedEpochMs) {
        /**
         * Validates the row.
         */
        public GateVerdictRow {
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(gate, "gate");
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(runId, "runId");
        }
    }

    /**
     * One page of value rows (M28.1).
     *
     * @param rows the rows
     * @param nextCursor cursor for the next page, or empty when finished
     * @param appliedThroughBatch batch the page is consistent with
     * @param completedAtNanos monotonic time of the reply
     */
    record ValueStatsPage(java.util.List<akyrion.thepredict.domain.learn.ValueStatDelta> rows, String nextCursor,
                          long appliedThroughBatch, long completedAtNanos) implements StorageReply {
        /**
         * Copies the rows.
         */
        public ValueStatsPage {
            rows = java.util.List.copyOf(rows);
            Objects.requireNonNull(nextCursor, "nextCursor");
        }
    }

    /**
     * Artifact fingerprints loaded from SQLite (M28.9).
     *
     * @param sha256 fingerprints
     * @param completedAtNanos monotonic time
     */
    record ArtifactsLoaded(java.util.List<String> sha256, long completedAtNanos) implements StorageReply {
        /**
         * Copies the list.
         */
        public ArtifactsLoaded {
            sha256 = java.util.List.copyOf(sha256);
        }
    }

    /**
     * One page of choice rows (M29.1).
     *
     * @param rows the page
     * @param completedAtNanos monotonic time
     */
    record ChoiceStatsPage(java.util.List<akyrion.thepredict.domain.distill.ChoiceStatDelta> rows,
                           long completedAtNanos) implements StorageReply {
        /**
         * Copies the rows.
         */
        public ChoiceStatsPage {
            rows = java.util.List.copyOf(rows);
        }
    }

    /**
     * One page of agreement rows (M29.1).
     *
     * @param rows the page
     * @param completedAtNanos monotonic time
     */
    record AgreementStatsPage(java.util.List<akyrion.thepredict.domain.distill.AgreementStatDelta> rows,
                              long completedAtNanos) implements StorageReply {
        /**
         * Copies the rows.
         */
        public AgreementStatsPage {
            rows = java.util.List.copyOf(rows);
        }
    }
}
