package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.AggregateCache;
import akyrion.thepredict.engine.AggregateQuery;
import akyrion.thepredict.engine.AggregateStore;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.OutcomeStatDelta;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.StorageHealth;
import akyrion.thepredict.engine.StorageMetrics;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageWriterTest {
    @TempDir
    Path directory;

    @Test
    void offerReturnsImmediatelyDropsByTypeAndReadsPrecedeEvents() throws Exception {
        var gate = new java.util.concurrent.atomic.AtomicBoolean(true);
        var writer = open(true, false, 8, file -> new Writer() {
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
        }, 8);
        var started = System.nanoTime();
        var accepted = writer.offer(event("planned"));
        var elapsed = System.nanoTime() - started;
        assertTrue(accepted);
        assertTrue(elapsed < 1_000_000L);
        gate.set(false);
        writer.close(null, 3000);
        var dropping = open(true, false, 2, SegmentFiles.DISK, 8);
        assertTrue(dropping.offer(event("planned")));
        assertTrue(dropping.offer(event("planned")));
        assertTrue(!dropping.offer(event("result")));
        WriterHosts.until(() -> dropping.dropped("result") >= 1);
        dropping.close(null, 3000);
        var ordered = traced(true, true, 32, SegmentFiles.DISK, 8);
        WriterHosts.until(() -> ordered.passes() > 0 && !ordered.pruning());
        ordered.offer(event("planned"));
        ordered.request(new StorageWriter.Work.Query(new AggregateQuery.OutcomeStatsQuery(java.util.List.of())));
        WriterHosts.until(() -> ordered.trace().contains("read") && ordered.trace().contains("event"));
        var seen = ordered.trace();
        assertTrue(seen.indexOf("read") < seen.indexOf("event"));
        ordered.close(null, 3000);
    }

    @Test
    void threeIoFailuresInAMinuteFailEventsAndAFullDiskPauses() throws Exception {
        var opens = new AtomicInteger();
        var failing = open(true, true, 8, failing(opens, "boom"), 8);
        failing.offer(event("planned"));
        WriterHosts.until(() -> failing.eventsCause().equals("FAILED"));
        assertTrue(opens.get() >= 3);
        assertEquals(StorageState.READY, failing.health().state());
        failing.close(null, 3000);
        var diskOpens = new AtomicInteger();
        var full = open(true, true, 8, failing(diskOpens, "No space left on device"), 8);
        full.offer(event("planned"));
        WriterHosts.until(() -> full.pausedSize() && full.diskNotices() == 1);
        assertEquals(StorageState.READ_ONLY, full.health().state());
        assertEquals("DISK_FULL", full.health().cause());
        assertEquals(1, full.diskNotices());
        assertEquals("", full.eventsCause());
        WriterHosts.until(() -> full.dropped("paused") == 1);
        full.shortenDiskRetry(0);
        WriterHosts.until(() -> full.diskRetries() >= 1);
        full.shortenDiskRetry(300_000_000_000L);
        full.offer(event("planned"));
        WriterHosts.until(() -> diskOpens.get() >= 2 && full.pausedSize());
        assertEquals(1, full.diskNotices());
        full.close(null, 3000);
    }

    @Test
    void theCurrentRunBatchIsZeroAtOpenAndTheQueryReturnsThatRun() throws Exception {
        var records = directory.resolve("applied-records");
        var database = directory.resolve("applied.db");
        Files.createDirectories(records);
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, records.resolve("backups"));
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, "
                            + "sqlite_version, last_applied_batch) VALUES ('other', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', "
                            + "'3.49.1', 50)")) {
                statement.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
        var inbox = new Inbox<InboxEntry>(8);
        var writer = new StorageWriter(WriterHosts.spec(records, database, false, true, 8, SegmentFiles.DISK, 8), inbox);
        var applied = new AtomicLong(-1);
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.OutcomeStatsLoaded loaded && applied.get() < 0) {
                    applied.set(loaded.appliedThroughBatch());
                }
            });
            return applied.get() == 0;
        });
        var stat = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.2, 0, 0, false, false, false, 0, 0);
        var key = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                "v1:NONE:NONE");
        assertTrue(writer.submit(new AggregateBatch(4, List.of(new OutcomeStatDelta(key, stat, 4)), List.of(), List.of())));
        var appliedBatch = new AtomicInteger();
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.BatchApplied batch && batch.batchId() == 4) {
                    appliedBatch.incrementAndGet();
                }
            });
            return appliedBatch.get() == 1;
        });
        assertTrue(writer.request(new StorageWriter.Work.Query(new AggregateQuery.OutcomeStatsQuery(List.of()))));
        var queried = new AtomicLong(-1);
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.OutcomeStatsLoaded loaded) {
                    queried.set(loaded.appliedThroughBatch());
                }
            });
            return queried.get() == 4;
        });
        writer.close(null, 3000);
        try (var connection = SqliteConnections.open(database, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT last_applied_batch FROM runs WHERE run = 'other'")) {
            assertTrue(result.next());
            assertEquals(50, result.getLong(1));
        }
    }

    @Test
    void aCorruptDatabaseWithNoBackupOpensEmptyAndKeepsTheOriginal() throws Exception {
        var records = directory.resolve("empty-records");
        Files.createDirectories(records);
        var live = directory.resolve("empty.db");
        Files.writeString(live, "corrupt-empty");
        Files.writeString(Path.of(live.toString() + "-wal"), "wal-bytes");
        var writer = new StorageWriter(WriterHosts.spec(records, live, false, true, 8, SegmentFiles.DISK, 8),
                new Inbox<>(8));
        WriterHosts.until(() -> writer.health().state() == StorageState.READY && "EMPTY".equals(writer.health().cause()));
        assertTrue(Files.exists(live));
        assertTrue(RecoveryPolicy.quickCheck(live));
        var quarantined = corruptDatabases();
        assertEquals(1, quarantined.size());
        assertEquals("corrupt-empty", Files.readString(quarantined.getFirst()));
        assertEquals("wal-bytes", Files.readString(Path.of(quarantined.getFirst().toString() + "-wal")));
        writer.close(null, 3000);
    }

    @Test
    void aGoodBackupIsRestoredAndItsRowStaysVisible() throws Exception {
        var records = directory.resolve("restore-records");
        var backups = records.resolve("backups");
        Files.createDirectories(backups);
        var source = directory.resolve("source.db");
        SchemaMigrator.open(source, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, backups);
        try (var connection = SqliteConnections.open(source, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO outcome_stats(profile, tactic, movement, level, context, windows, effect_mean, effect_m2, "
                            + "dealt_sum, taken_sum, reached, mob_died, target_died, casts, hits, first_epoch_ms, "
                            + "last_epoch_ms, extra) VALUES ('restored', 'keep_distance', 'APPROACH', 'JEV', "
                            + "'v1:NONE:NONE', 42, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL)")) {
                statement.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
        Files.copy(source, backups.resolve("aggregates-20261006T010000Z.db"));
        var live = directory.resolve("restore.db");
        Files.writeString(live, "corrupt-live");
        var writer = new StorageWriter(WriterHosts.spec(records, live, false, true, 8, SegmentFiles.DISK, 8),
                new Inbox<>(8));
        WriterHosts.until(() -> writer.health().state() == StorageState.READY
                && "RESTORED".equals(writer.health().cause()));
        writer.close(null, 3000);
        try (var connection = SqliteConnections.open(live, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT windows FROM outcome_stats WHERE profile = 'restored'")) {
            assertTrue(result.next());
            assertEquals(42, result.getLong(1));
        }
        assertEquals("corrupt-live", Files.readString(corruptDatabases().getFirst()));
    }

    @Test
    void pruneZeroKeepsTodayAndAWatcherTimesOut() throws Exception {
        var records = directory.resolve("prune-records");
        var events = records.resolve("events");
        var today = LocalDate.now(ZoneOffset.UTC).toString();
        Files.createDirectories(events.resolve(today));
        Files.createDirectories(events.resolve("2000-01-01"));
        Files.writeString(events.resolve(today).resolve("keep.jsonl"), "today");
        Files.writeString(events.resolve("2000-01-01").resolve("drop.jsonl"), "old");
        var pruned = AdminTasks.run(new AdminCommand.Prune(0), records, directory.resolve("unused.db"), null, null, 7);
        assertTrue(pruned.startsWith("pruned"));
        assertTrue(Files.isDirectory(events.resolve(today)));
        assertTrue(Files.exists(events.resolve(today).resolve("keep.jsonl")));
        assertFalse(Files.exists(events.resolve("2000-01-01")));
        var service = StorageService.open(WriterHosts.spec(records, directory.resolve("watch.db"), false, true, 8,
                SegmentFiles.DISK, 8), RecordLevel.OFF, RecordBodies.NONE, QuestionDetail.FULL,
                AnswerCacheMode.OFF, new Inbox<>(8), null, () -> {
        }, () -> 1L);
        var results = new ArrayList<StorageReply.AdminResult>();
        assertTrue(service.submit(new AdminCommand.Verify(), results::add));
        service.expire(System.nanoTime() + StorageService.WATCHER_NANOS + 1_000_000L);
        assertEquals(1, results.size());
        assertFalse(results.getFirst().ok());
        assertEquals("timed out", results.getFirst().detail());
        assertFalse(service.atCapacity());
        service.close(null, 3000);
    }

    @Test
    void aDedupBodyGoesToTheBodiesSegment() throws Exception {
        var records = directory.resolve("body-records");
        var service = StorageService.open(WriterHosts.spec(records, directory.resolve("body.db"), true, false, 8,
                SegmentFiles.DISK, 8), RecordLevel.DECISIONS, RecordBodies.DEDUP, QuestionDetail.FULL,
                AnswerCacheMode.OFF, new Inbox<>(8), null, () -> {
        }, () -> 1L);
        service.tap().requestBody(new EncounterId(3), 1, "body-payload-z9xq");
        service.tap().requestBody(new EncounterId(3), 2, "body-payload-z9xq");
        service.close(null, 3000);
        var bodyFiles = new ArrayList<Path>();
        var eventText = new StringBuilder();
        try (var days = Files.list(records.resolve("events"))) {
            for (var day : days.toList()) {
                try (var files = Files.list(day)) {
                    for (var file : files.toList()) {
                        var name = file.getFileName().toString();
                        if (name.startsWith("bodies-20261006T120000Z-0a1b-")) {
                            bodyFiles.add(file);
                        }
                        if (name.startsWith("events-")) {
                            eventText.append(Files.readString(file));
                        }
                    }
                }
            }
        }
        assertEquals(1, bodyFiles.size());
        var bodyText = Files.readString(bodyFiles.getFirst());
        assertTrue(bodyText.contains("body-payload-z9xq"));
        assertTrue(eventText.toString().contains("body_ref"));
        assertFalse(eventText.toString().contains("body-payload-z9xq"));
    }

    @Test
    void aRingFullForAMinutePublishesDegradedOnStatus() throws Exception {
        var cacheInbox = new Inbox<InboxEntry>(8);
        var cache = new AggregateCache(accepting(), new StorageMetrics(), 4096, 100, 4, cacheInbox);
        for (var index = 0; index < 8; index++) {
            cache.outcomeClosed("guardian", new OutcomeWindow.Summary(ControlLevel.JEV, new TacticId("keep_distance"),
                    Movement.APPROACH, 0, 1, 0, 0, false, 0, 0, false, false, index), index);
            assertTrue(cache.flush(0, 1024).isPresent());
        }
        assertTrue(cache.flush(0, 1024).isEmpty());
        assertFalse(cache.degraded());
        assertTrue(cache.flush(AggregateCache.DEGRADED_AFTER_NANOS, 1024).isEmpty());
        assertTrue(cache.degraded());
        var records = directory.resolve("degraded-records");
        var service = StorageService.open(WriterHosts.spec(records, directory.resolve("degraded.db"), false, true, 8,
                SegmentFiles.DISK, 8), RecordLevel.OFF, RecordBodies.NONE, QuestionDetail.FULL,
                AnswerCacheMode.OFF, new Inbox<>(8), cache, () -> {
        }, () -> 1L);
        WriterHosts.until(() -> service.writer().health().state() == StorageState.READY);
        assertEquals(StorageState.DEGRADED, service.status().state());
        assertEquals(StorageState.DEGRADED, service.health().state());
        service.close(null, 3000);
    }

    private StorageWriter open(boolean records, boolean aggregates, int queue, SegmentFiles.Opener opener, int replies)
            throws IOException {
        return open(records, aggregates, queue, opener, replies, WriterTrace.OFF);
    }

    private StorageWriter traced(boolean records, boolean aggregates, int queue, SegmentFiles.Opener opener,
                                 int replies) throws IOException {
        return open(records, aggregates, queue, opener, replies, WriterTrace.bounded(WriterTrace.MAX_WORDS));
    }

    private StorageWriter open(boolean records, boolean aggregates, int queue, SegmentFiles.Opener opener, int replies,
                               WriterTrace trace) throws IOException {
        var recordsDir = directory.resolve("records-" + System.nanoTime());
        java.nio.file.Files.createDirectories(recordsDir);
        return new StorageWriter(WriterHosts.spec(recordsDir, directory.resolve("db-" + System.nanoTime() + ".db"),
                records, aggregates, queue, opener, replies), new Inbox<InboxEntry>(8), trace);
    }

    private static RecordEvent event(String type) {
        return new RecordEvent(type, "20261006T120000Z-0a1b", 1L, 2L, 3L, "", java.util.Map.of());
    }

    private static SegmentFiles.Opener failing(AtomicInteger opens, String message) {
        return file -> {
            opens.incrementAndGet();
            return new Writer() {
                @Override
                public void write(char[] buffer, int offset, int length) throws IOException {
                    throw new IOException(message);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
        };
    }

    private java.util.List<Path> corruptDatabases() throws IOException {
        var found = new ArrayList<Path>();
        try (var stream = Files.list(directory)) {
            for (var path : stream.toList()) {
                var name = path.getFileName().toString();
                if (name.startsWith("aggregates.corrupt-") && name.endsWith(".db")) {
                    found.add(path);
                }
            }
        }
        return found;
    }

    private static AggregateStore accepting() {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                return true;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return true;
            }
        };
    }
}
