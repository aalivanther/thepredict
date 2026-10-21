package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.AggregateCache;
import akyrion.thepredict.engine.AggregateQuery;
import akyrion.thepredict.engine.AggregateStore;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.MemoryCounters;
import akyrion.thepredict.engine.PlayerMemoryDelta;
import akyrion.thepredict.engine.StorageHealth;
import akyrion.thepredict.engine.StorageMetrics;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryBatchTest {
    @TempDir
    Path directory;

    @Test
    void memoryRowsCommitWithTheBatchAndARepeatIsIdempotent() throws Exception {
        var file = directory.resolve("aggregates.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var subject = new UUID(4, 5);
        var bytes = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes();
        var batch = new AggregateBatch(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PlayerMemoryDelta(subject, bytes, 0, 20, false)));
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            assertTrue(BatchApply.apply(connection, batch, "run"));
            assertEquals(1, PlayerMemorySql.count(connection));
            assertTrue(BatchApply.apply(connection, batch, "run"));
            assertEquals(1, PlayerMemorySql.count(connection));
        }
    }

    @Test
    void aHundredBatchesEachAddOneFightAndAFailedMemoryBatchIsCounted() throws Exception {
        var file = directory.resolve("hundred.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var subject = new UUID(4, 6);
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            for (var id = 1; id <= 100; id++) {
                var digest = new akyrion.thepredict.engine.FightDigest(subject, 2,
                        akyrion.thepredict.domain.FightOutcome.WIN, akyrion.thepredict.domain.PartyBand.ONE,
                        akyrion.thepredict.domain.DifficultySection.SOLO, java.util.Optional.empty(), id, false);
                var fold = new akyrion.thepredict.engine.MemoryFold(List.of(digest), java.util.Optional.empty(), 1L,
                        MemorySettings.DEFAULT);
                var delta = new PlayerMemoryDelta(subject, new byte[0], 0, id, false, java.util.Optional.of(fold));
                assertTrue(BatchApply.apply(connection, new AggregateBatch(id, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(delta)), "run"));
            }
            var decoded = PlayerMemoryCodec.decode(PlayerMemorySql.load(connection, subject).orElseThrow());
            assertEquals(100, ((MemoryDecoded.Ready) decoded).memory().fights());
            try (var statement = connection.prepareStatement("SELECT last_applied_batch FROM runs WHERE run = 'run'")) {
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(100, result.getLong(1));
                }
            }
        }
        var inbox = new Inbox<InboxEntry>(8);
        var counters = new MemoryCounters();
        var cache = new AggregateCache(accepting(), new StorageMetrics(), 4096, 100, 4, inbox);
        cache.onMemoryFailed(batch -> counters.add(MemoryCounters.Kind.BATCH_FAILED, 1));
        cache.acceptMemory(List.of(new PlayerMemoryDelta(subject, new byte[] {1}, 0, 1, false)));
        var sent = cache.flush(0, 1024).orElseThrow();
        inbox.offerStorage(new StorageReply.BatchFailed(sent.batchId(), "MEMORY_BATCH_FAILED", 1));
        cache.drain(4);
        assertEquals(1, counters.get(MemoryCounters.Kind.BATCH_FAILED));
    }

    @Test
    void killingTheChildAfterCommitKeepsEachFightOnce() throws Exception {
        var database = directory.resolve("crash.db");
        var marker = directory.resolve("ready");
        var executable = Path.of(System.getProperty("java.home"), "bin", "java");
        var process = new ProcessBuilder(executable.toString(), "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("java.class.path"), MemoryCrashChild.class.getName(), database.toString(),
                marker.toString()).redirectErrorStream(true).start();
        var output = new StringBuilder();
        var deadline = System.nanoTime() + 30_000_000_000L;
        try (var reader = process.inputReader(java.nio.charset.StandardCharsets.UTF_8)) {
            while (System.nanoTime() < deadline && !java.nio.file.Files.exists(marker)) {
                if (reader.ready()) {
                    output.append((char) reader.read());
                } else {
                    java.util.concurrent.locks.LockSupport.parkNanos(5_000_000L);
                }
            }
        }
        assertTrue(java.nio.file.Files.exists(marker), output.toString());
        process.destroyForcibly();
        process.waitFor();
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            var decoded = PlayerMemoryCodec.decode(PlayerMemorySql.load(connection, MemoryCrashChild.SUBJECT).orElseThrow());
            assertEquals(1, ((MemoryDecoded.Ready) decoded).memory().fights());
            try (var statement = connection.prepareStatement("SELECT last_applied_batch FROM runs WHERE run = 'run'")) {
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getLong(1));
                }
            }
            assertTrue(BatchApply.apply(connection, MemoryCrashChild.batch(), "run"));
            decoded = PlayerMemoryCodec.decode(PlayerMemorySql.load(connection, MemoryCrashChild.SUBJECT).orElseThrow());
            assertEquals(1, ((MemoryDecoded.Ready) decoded).memory().fights());
        }
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
