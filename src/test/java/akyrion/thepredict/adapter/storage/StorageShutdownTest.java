package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.OutcomeStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageShutdownTest {
    @TempDir
    Path directory;

    @Test
    void aFullQueueStillWritesRunStopAndADirectBatchApplies() throws Exception {
        var records = directory.resolve("records");
        var database = directory.resolve("aggregates.db");
        Files.createDirectories(records);
        var gate = new AtomicBoolean(true);
        var opened = new AtomicBoolean();
        var writer = new StorageWriter(WriterHosts.spec(records, database, true, true, 2, file -> {
            opened.set(true);
            return new java.io.Writer() {
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
        }, 32), new Inbox<InboxEntry>(8));
        writer.offer(event());
        WriterHosts.until(opened::get);
        var rejected = false;
        for (var id = 1; id <= 80; id++) {
            if (!writer.submit(batch(id, "queued"))) {
                rejected = true;
            }
        }
        assertTrue(rejected);
        gate.set(false);
        var started = System.nanoTime();
        writer.close(batch(90, "direct"), 3000);
        assertTrue(System.nanoTime() - started <= 3_000_000_000L);
        try (var connection = SqliteConnections.open(database, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM outcome_stats WHERE profile = 'direct'")) {
            assertEquals(1, result.getInt(1));
        }
        assertEquals(0, writer.dropped("aggregate"), "no queued batch failed");
        var plain = directory.resolve("plain");
        Files.createDirectories(plain);
        var disk = new StorageWriter(WriterHosts.spec(plain, directory.resolve("plain.db"), true, false, 8,
                SegmentFiles.DISK, 8), new Inbox<InboxEntry>(4));
        disk.offer(event());
        disk.close(null, 3000);
        var text = Files.walk(plain).filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (java.io.IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                }).reduce("", String::concat);
        assertTrue(text.contains("\"type\":\"run_stop\""));
    }

    @Test
    void aBlockedDiskReturnsWithinThreeSecondsAndNamesWhatWasLeft() throws Exception {
        var gate = new AtomicBoolean(true);
        var records = directory.resolve("blocked");
        Files.createDirectories(records);
        var writer = new StorageWriter(WriterHosts.spec(records, directory.resolve("blocked.db"), true, false, 4,
                file -> new java.io.Writer() {
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
                }, 8), new Inbox<InboxEntry>(4));
        writer.offer(event());
        var started = System.nanoTime();
        writer.close(null, 3000);
        assertTrue(System.nanoTime() - started < 3_500_000_000L);
        assertTrue(writer.daemon());
        assertTrue(writer.alive());
        assertTrue(!writer.unapplied().isEmpty());
        gate.set(false);
        WriterHosts.until(() -> !writer.alive());
    }

    private static RecordEvent event() {
        return new RecordEvent("planned", "20261006T120000Z-0a1b", 1L, 2L, 3L, "", java.util.Map.of());
    }

    private static AggregateBatch batch(long id, String profile) {
        var key = new OutcomeStatKey(profile, new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                "v1:NONE:NONE");
        return new AggregateBatch(id, List.of(new OutcomeStatDelta(key, OutcomeStat.EMPTY, id)), List.of(), List.of());
    }
}
