package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.CounterMemory;
import akyrion.thepredict.domain.DifficultySample;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.PlayerMemoryMath;
import akyrion.thepredict.domain.StyleMemory;
import akyrion.thepredict.engine.FightDigest;
import akyrion.thepredict.engine.MemoryFold;
import akyrion.thepredict.engine.PlayerMemoryDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMemorySqlTest {
    @TempDir
    Path directory;

    @Test
    void aRowRoundTripsAndASecondApplyBumpsTheRevision() throws Exception {
        var file = directory.resolve("aggregates.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var subject = new UUID(1, 2);
        var bytes = PlayerMemoryCodec.encode(PlayerMemory.empty(), MemorySettings.DEFAULT.maxProfilesPerPlayer()).bytes();
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(subject, bytes, 0, 10, false));
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(subject, bytes, 1, 11, false));
            assertTrue(Arrays.equals(bytes, PlayerMemorySql.load(connection, subject).orElseThrow()));
            assertEquals(1, PlayerMemorySql.count(connection));
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(subject, new byte[0], 0, 12, true));
            assertTrue(PlayerMemorySql.load(connection, subject).isEmpty());
        }
    }

    @Test
    void oneTwoAndEightDigestsApplyInTimeOrderAndADeathDoesNotRaiseTheRung() throws Exception {
        var file = directory.resolve("fold.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var subject = new UUID(8, 1);
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            var one = fold(subject, List.of(digest(subject, 7, 10, 3f, false, List.of())));
            PlayerMemorySql.apply(connection, one);
            var decoded = ready(connection, subject);
            assertEquals(1, decoded.fights());
            assertEquals(3 * 16, decoded.profiles().getFirst().rungs()[0]);
            var pair = fold(subject, List.of(
                    digest(subject, 7, 30, 0f, false, List.of()),
                    digest(subject, 9, 30, 1f, false, List.of())));
            PlayerMemorySql.apply(connection, pair);
            decoded = ready(connection, subject);
            assertEquals(2, decoded.fights());
            assertEquals(2, decoded.profiles().size());
            var eight = new ArrayList<FightDigest>();
            for (var index = 0; index < 8; index++) {
                eight.add(digest(subject, 11, 100 + index, 4f, false, List.of()));
            }
            PlayerMemorySql.apply(connection, fold(subject, eight));
            decoded = ready(connection, subject);
            assertEquals(10, decoded.fights());
            connection.createStatement().executeUpdate("DELETE FROM m27_player_memory");
            var ordered = fold(subject, List.of(
                    digest(subject, 4, 50, 0f, false, List.of()),
                    digest(subject, 4, 10, 8f, false, List.of())));
            PlayerMemorySql.apply(connection, ordered);
            decoded = ready(connection, subject);
            assertEquals(112, decoded.profiles().getFirst().rungs()[0]);
            PlayerMemorySql.apply(connection, fold(subject, List.of(digest(subject, 4, 80, 8f, true, List.of()))));
            decoded = ready(connection, subject);
            assertEquals(112, decoded.profiles().getFirst().rungs()[0]);
        }
    }

    @Test
    void styleCountersHistogramsAndMomentsAreStoredByTheFusion() throws Exception {
        var file = directory.resolve("style.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var subject = new UUID(8, 2);
        var shape = new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS];
        shape[0][0] = (byte) 255;
        var mass = new float[StyleMemory.HISTOGRAMS];
        mass[0] = 10f;
        var fight = new StyleMemory(1f, 1, masses(0f), masses(30f), new int[StyleMemory.PATTERNS],
                new float[StyleMemory.ACTIONS], 0f, new int[0], new float[0], mass, shape, 2f, 4f, 1f,
                new float[StyleMemory.EPISODES], new float[StyleMemory.EPISODES]);
        var stored = new PlayerMemory(1, 1, 1, Optional.empty(), Optional.empty(),
                Optional.of(new StyleMemory(1f, 1, masses(20f), masses(2f), new int[StyleMemory.PATTERNS],
                        new float[StyleMemory.ACTIONS], 0f, new int[0], new float[0],
                        new float[StyleMemory.HISTOGRAMS], new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS],
                        0f, 0f, 0f, new float[StyleMemory.EPISODES], new float[StyleMemory.EPISODES])),
                List.of());
        var bytes = PlayerMemoryCodec.encode(stored, 8).bytes();
        var counters = List.of(new CounterMemory(1, 4f, 10f), new CounterMemory(2, 3f, 9f),
                new CounterMemory(3, 1f, 2f));
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(subject, bytes, 1, 1, false));
            var delta = new PlayerMemoryDelta(subject, new byte[0], 0, 2, false, Optional.of(new MemoryFold(
                    List.of(digest(subject, 4, 2, 1f, false, counters)), Optional.of(fight), 1L,
                    MemorySettings.DEFAULT)));
            assertEquals(PlayerMemorySql.ApplyResult.WROTE, PlayerMemorySql.apply(connection, delta));
            var memory = ready(connection, subject);
            var style = memory.style().orElseThrow();
            var expected = PlayerMemoryMath.update(18.0, 1.8, 0, 0, 30, 1.0, 4, 0.25, PlayerMemoryMath.HABIT_THETA);
            assertTrue(expected.breaks() >= 1);
            assertEquals(expected.successes(), style.success(0), 1.0);
            assertEquals(expected.failures(), style.failure(0), 1.0);
            var histogram = 0;
            for (var bin = 0; bin < StyleMemory.BINS; bin++) {
                histogram += style.histogramShape(0)[bin] & 0xFF;
            }
            assertEquals(255, histogram);
            assertEquals(4f, style.reactionMean(), 0.05f);
            assertEquals(2, memory.profiles().getFirst().counters().size());
            assertTrue(memory.profiles().getFirst().counters().stream().noneMatch(counter -> counter.pattern() == 3));
        }
    }

    @Test
    void corruptAndNewerRowsAreLeftUntouched() throws Exception {
        var file = directory.resolve("guard.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var corruptId = new UUID(8, 3);
        var newerId = new UUID(8, 4);
        var corrupt = new byte[] {0, 0, 0, 0};
        var valid = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes();
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            insert(connection, corruptId, 1, corrupt);
            insert(connection, newerId, 2, valid);
            var fold = fold(corruptId, List.of(digest(corruptId, 1, 5, 2f, false, List.of())));
            assertEquals(PlayerMemorySql.ApplyResult.SKIPPED_CORRUPT, PlayerMemorySql.apply(connection, fold));
            assertTrue(Arrays.equals(corrupt, PlayerMemorySql.load(connection, corruptId).orElseThrow()));
            var newer = new PlayerMemoryDelta(newerId, new byte[0], 0, 6, false, Optional.of(new MemoryFold(
                    List.of(digest(newerId, 1, 6, 2f, false, List.of())), Optional.empty(), 1L,
                    MemorySettings.DEFAULT)));
            assertEquals(PlayerMemorySql.ApplyResult.SKIPPED_NEWER, PlayerMemorySql.apply(connection, newer));
            try (var statement = connection.prepareStatement(
                    "SELECT record_v, record FROM m27_player_memory WHERE subject = ?")) {
                statement.setBytes(1, PlayerMemorySql.bytes(newerId));
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                    assertTrue(Arrays.equals(valid, result.getBytes(2)));
                }
            }
        }
    }

    @Test
    void aResetAfterAnInFlightBatchStartsTheNextDigestAtZero() throws Exception {
        var file = directory.resolve("reset.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var subject = new UUID(8, 5);
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            var first = batch(1, subject, List.of(digest(subject, 3, 1, 1f, false, List.of())));
            var inflight = batch(2, subject, List.of(digest(subject, 3, 2, 1f, false, List.of())));
            assertTrue(BatchApply.apply(connection, first, "run"));
            var delete = new PlayerMemoryDelta(subject, new byte[0], 0, 9, true);
            var reset = new akyrion.thepredict.engine.AggregateBatch(3, List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(delete));
            assertTrue(BatchApply.apply(connection, inflight, "run"));
            assertEquals(2, ready(connection, subject).fights());
            assertTrue(BatchApply.apply(connection, reset, "run"));
            assertTrue(PlayerMemorySql.load(connection, subject).isEmpty());
            var next = batch(4, subject, List.of(digest(subject, 3, 4, 1f, false, List.of())));
            assertTrue(BatchApply.apply(connection, next, "run"));
            assertEquals(1, ready(connection, subject).fights());
            assertTrue(BatchApply.apply(connection, next, "run"));
            assertEquals(1, ready(connection, subject).fights());
        }
    }

    private static void insert(java.sql.Connection connection, UUID subject, int recordV, byte[] record)
            throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO m27_player_memory(subject, record_v, record, fights, rev, first_epoch_ms, updated_epoch_ms) "
                        + "VALUES (?, ?, ?, 0, 1, 1, 1)")) {
            statement.setBytes(1, PlayerMemorySql.bytes(subject));
            statement.setInt(2, recordV);
            statement.setBytes(3, record);
            statement.executeUpdate();
        }
    }

    private static PlayerMemory ready(java.sql.Connection connection, UUID subject) throws Exception {
        var decoded = PlayerMemoryCodec.decode(PlayerMemorySql.load(connection, subject).orElseThrow());
        return ((MemoryDecoded.Ready) decoded).memory();
    }

    private static PlayerMemoryDelta fold(UUID subject, List<FightDigest> digests) {
        return new PlayerMemoryDelta(subject, new byte[0], 0, 3, false,
                Optional.of(new MemoryFold(digests, Optional.empty(), 1L, MemorySettings.DEFAULT)));
    }

    private static akyrion.thepredict.engine.AggregateBatch batch(long id, UUID subject, List<FightDigest> digests) {
        return new akyrion.thepredict.engine.AggregateBatch(id, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(fold(subject, digests)));
    }

    private static FightDigest digest(UUID subject, int hash, long millis, float estimate, boolean death,
                                     List<CounterMemory> counters) {
        return new FightDigest(subject, hash, death ? FightOutcome.LOSS : FightOutcome.WIN, PartyBand.ONE,
                DifficultySection.SOLO, Optional.of(new DifficultySample(1f, estimate)), millis, false, counters);
    }

    private static float[] masses(float value) {
        var values = new float[StyleMemory.PATTERNS];
        values[0] = value;
        return values;
    }
}
