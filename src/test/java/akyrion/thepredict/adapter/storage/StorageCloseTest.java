package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-11: what a close leaves is reported, the lock outlives a late thread, and a lost final batch is not clean. */
class StorageCloseTest {
    @TempDir
    Path directory;

    @Test
    void anUnfinishedCloseKeepsTheLockUntilTheLateThreadEnds() throws Exception {
        var records = directory.resolve("records");
        var gate = new AtomicBoolean(true);
        var spec = WriterHosts.spec(records, directory.resolve("late.db"), true, false, 8, file -> blocking(gate), 8);
        var first = open(spec);
        assertTrue(first.writer().offer(new RecordEvent("planned", spec.run(), 1L, 2L, 3L, "", Map.of())));
        var report = first.close(200);
        assertFalse(report.finished());
        assertTrue(report.unapplied().startsWith("batches="), report.unapplied());
        assertTrue(first.writer().alive());
        var second = open(spec);
        assertEquals("LOCK_HELD", second.status().cause());
        gate.set(false);
        WriterHosts.until(() -> !first.writer().alive());
        var third = open(spec);
        assertTrue(third.writer() != null);
        assertTrue(third.close(3000).finished());
    }

    @Test
    void aFinalBatchThatFailsMarksTheRunDirtyAndAnAppliedOneClean() throws Exception {
        var records = directory.resolve("dirty-records");
        Files.createDirectories(records);
        var database = directory.resolve("dirty.db");
        var spec = WriterHosts.spec(records, database, true, true, 8, SegmentFiles.DISK, 8);
        var writer = new StorageWriter(spec, new Inbox<InboxEntry>(8));
        WriterHosts.until(() -> writer.health().state() == StorageState.READY);
        assertTrue(writer.close(finalBatch(Double.NaN), 3000).finished());
        assertEquals(0, cleanFlag(database));
        assertTrue(events(records).contains("\"clean\":false"), events(records));
        var again = new StorageWriter(nextRun(spec), new Inbox<InboxEntry>(8));
        WriterHosts.until(() -> again.health().state() == StorageState.READY);
        assertEquals("DIRTY_SHUTDOWN", again.health().cause());
        assertTrue(again.close(3000).finished());
        assertEquals(1, cleanFlag(database));
    }

    /**
     * The final batch is lost without a counted drop when the store is read-only at the close, here after a full disk:
     * only the result of the final apply can mark that run dirty.
     */
    @Test
    void aFinalBatchTheReadOnlyStoreCannotTakeMarksTheRunDirty() throws Exception {
        var records = directory.resolve("full-records");
        Files.createDirectories(records);
        var database = directory.resolve("full.db");
        var spec = WriterHosts.spec(records, database, true, true, 8, file -> full(), 8);
        var writer = new StorageWriter(spec, new Inbox<InboxEntry>(8));
        WriterHosts.until(() -> writer.health().state() == StorageState.READY);
        assertTrue(writer.offer(new RecordEvent("planned", spec.run(), 1L, 2L, 3L, "", Map.of())));
        WriterHosts.until(() -> writer.dropped("paused") == 1);
        assertEquals(StorageState.READ_ONLY, writer.health().state());
        assertTrue(writer.close(finalBatch(0.5), 3000).finished());
        assertEquals(1, writer.droppedEvents(), "the lost batch is not a counted drop");
        assertEquals(0, cleanFlag(database));
        try (var connection = SqliteConnections.readOnly(database);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM m28_value_stats")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    private static AggregateBatch finalBatch(double rewardMean) {
        var delta = new ValueStatDelta(new ValueStatKey("guardian", "strike", "v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE",
                "sim", "r1", ValueStatKey.week(System.currentTimeMillis())), 1, rewardMean, 0.0, 0.0, 0.0, 0.0, 0, 0,
                System.currentTimeMillis());
        return new AggregateBatch(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(delta));
    }

    private static Writer full() {
        return new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws java.io.IOException {
                throw new java.io.IOException("No space left on device");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    private static StorageWriter.Spec nextRun(StorageWriter.Spec spec) {
        return new StorageWriter.Spec(spec.directory(), spec.database(), spec.records(), spec.aggregates(),
                spec.queueCapacity(), spec.segmentBytes(), spec.segmentNanos(), spec.compress(), spec.retentionDays(),
                spec.maxBytes(), spec.synchronous(), spec.busyTimeoutMillis(), spec.backupHours(), spec.backupsKept(),
                "20261006T130000Z-0a1c", spec.pluginVersion(), spec.model(), spec.configSha256(), spec.opener(),
                spec.replyCapacity(), spec.limits());
    }

    private StorageService open(StorageWriter.Spec spec) {
        return StorageService.open(spec, RecordLevel.DECISIONS, RecordBodies.NONE, QuestionDetail.FULL,
                AnswerCacheMode.OFF, new Inbox<>(8), null, () -> {
        }, () -> 1L);
    }

    private static int cleanFlag(Path database) throws Exception {
        try (var connection = SqliteConnections.readOnly(database);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT clean_shutdown FROM runs ORDER BY started_epoch_ms DESC, rowid DESC LIMIT 1")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String events(Path records) throws Exception {
        try (var walk = Files.walk(records.resolve("events"))) {
            var text = new StringBuilder();
            for (var file : walk.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList()) {
                text.append(Files.readString(file));
            }
            return text.toString();
        }
    }

    private static Writer blocking(AtomicBoolean gate) {
        return new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) {
                while (gate.get()) {
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }
}
