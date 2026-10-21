package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.distill.AgreementStatDelta;
import akyrion.thepredict.domain.distill.ChoiceStatDelta;
import akyrion.thepredict.domain.distill.ChoiceStatKey;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.PlayerMemoryDelta;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-05: the A5 caps and retentions of {@code m27}, {@code m28} and {@code m29} hold in production. */
class TableUpkeepTest {
    private static final long DAY_MS = 86_400_000L;
    private static final long NOW = System.currentTimeMillis();
    private static final int WEEK = ValueStatKey.week(NOW);
    private static final StorageLimits SMALL = new StorageLimits(LearningSettings.DEFAULT, 10, 180, 8, 1000, 1000,
            1000);

    @TempDir
    Path directory;

    @Test
    void aCapOnTwelveHundredRowsOfOneWeekDeletesTheOldestAndKeepsThatWeek() throws Exception {
        try (var connection = database("choices")) {
            for (var row = 0; row < 1200; row++) {
                ChoiceStatSql.upsert(connection, choice(row, WEEK, NOW - 1200 + row));
            }
            var upkeep = drain(connection);
            assertEquals(1000, count(connection, "SELECT COUNT(*) FROM m29_choice_stats"));
            assertEquals(1000, count(connection, "SELECT COUNT(*) FROM m29_choice_stats WHERE week = " + WEEK));
            assertEquals(NOW - 1000, count(connection, "SELECT MIN(last_epoch_ms) FROM m29_choice_stats"));
            assertEquals(200, upkeep.removed(TableUpkeep.DISTILL_TRUNCATED));
        }
    }

    @Test
    void valueRowsLoseTheirOldWeeksAndThenThePacedRowsFirst() throws Exception {
        try (var connection = database("values")) {
            for (var row = 0; row < 600; row++) {
                ValueStatSql.upsert(connection, value("t" + row, "player_full", WEEK, NOW + row));
                ValueStatSql.upsert(connection, value("t" + row, "player_paced", WEEK, NOW + row));
            }
            for (var row = 0; row < 50; row++) {
                ValueStatSql.upsert(connection, value("old" + row, "sim", WEEK - 20, NOW - 20 * 7 * DAY_MS));
            }
            var upkeep = drain(connection);
            assertEquals(600, count(connection, "SELECT COUNT(*) FROM m28_value_stats WHERE source = 'player_full'"));
            assertEquals(400, count(connection, "SELECT COUNT(*) FROM m28_value_stats WHERE source = 'player_paced'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM m28_value_stats WHERE week < " + (WEEK - 8)));
            assertEquals(50, upkeep.removed(TableUpkeep.VALUE_EXPIRED));
            assertEquals(200, upkeep.removed(TableUpkeep.VALUE_TRUNCATED));
        }
    }

    @Test
    void agreementRowsKeepTheirCapAndDistillWeeks() throws Exception {
        try (var connection = database("agreements")) {
            for (var row = 0; row < 1100; row++) {
                AgreementStatSql.upsert(connection, new AgreementStatDelta("a1", "p", "r1:ALL", "c" + row, WEEK, 1,
                        0.0, 0.0, 1, 1, 0, 0, NOW + row));
            }
            AgreementStatSql.upsert(connection, new AgreementStatDelta("a1", "p", "r1:ALL", "stale", WEEK - 9, 1,
                    0.0, 0.0, 1, 1, 0, 0, NOW));
            var upkeep = drain(connection);
            assertEquals(1000, count(connection, "SELECT COUNT(*) FROM m29_agreement_stats"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM m29_agreement_stats WHERE context = 'stale'"));
            assertEquals(1, upkeep.removed(TableUpkeep.DISTILL_EXPIRED));
            assertEquals(100, upkeep.removed(TableUpkeep.DISTILL_TRUNCATED));
        }
    }

    @Test
    void memoryRowsExpireByRetentionAndThenTheOldestGoAboveTheCap() throws Exception {
        try (var connection = database("memory")) {
            for (var row = 0; row < 5; row++) {
                memory(connection, new UUID(1, row), NOW - 400 * DAY_MS);
            }
            for (var row = 0; row < 15; row++) {
                memory(connection, new UUID(2, row), NOW - row);
            }
            var upkeep = drain(connection);
            assertEquals(10, PlayerMemorySql.count(connection));
            assertEquals(NOW - 9, count(connection, "SELECT MIN(updated_epoch_ms) FROM m27_player_memory"));
            assertEquals(5, upkeep.removed(TableUpkeep.MEMORY_EXPIRED));
            assertEquals(5, upkeep.removed(TableUpkeep.MEMORY_TRUNCATED));
        }
    }

    @Test
    void theWriterPrunesWhenTheStoreOpens() throws Exception {
        var file = directory.resolve("writer.db");
        try (var connection = database("writer")) {
            for (var row = 0; row < 1200; row++) {
                ChoiceStatSql.upsert(connection, choice(row, WEEK, NOW - 1200 + row));
            }
        }
        var records = directory.resolve("records");
        Files.createDirectories(records);
        var writer = new StorageWriter(WriterHosts.spec(records, file, SMALL), new Inbox<InboxEntry>(8));
        WriterHosts.until(() -> writer.health().state() == StorageState.READY && !writer.pruning()
                && writer.pruned(TableUpkeep.DISTILL_TRUNCATED) == 200);
        assertTrue(writer.close(3000).finished());
        try (var connection = SqliteConnections.readOnly(file)) {
            assertEquals(1000, count(connection, "SELECT COUNT(*) FROM m29_choice_stats"));
        }
    }

    @Test
    void aCapAboveTheOneOfA5IsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new StorageLimits(LearningSettings.DEFAULT, 10, 180, 8,
                ValueStatSql.CAP + 1, 1000, 1000));
        assertThrows(IllegalArgumentException.class, () -> new StorageLimits(LearningSettings.DEFAULT, 10, 180, 8,
                1000, ChoiceStatSql.CAP + 1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new StorageLimits(LearningSettings.DEFAULT, 10, 180, 8,
                1000, 1000, AgreementStatSql.CAP + 1));
        assertEquals(ValueStatSql.CAP, StorageLimits.DEFAULTS.valueRows());
        assertFalse(StorageLimits.DEFAULTS.memoryRows() < 1000);
    }

    private TableUpkeep drain(Connection connection) throws Exception {
        var upkeep = new TableUpkeep(SMALL);
        upkeep.start(NOW);
        var statements = 0;
        while (upkeep.active()) {
            assertTrue(upkeep.step(connection));
            statements++;
            assertTrue(statements < 100, "the pass ends");
        }
        assertFalse(upkeep.step(connection));
        return upkeep;
    }

    private Connection database(String name) throws Exception {
        var file = directory.resolve(name.equals("writer") ? "writer.db" : name + ".db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        return SqliteConnections.open(file, "normal", 2000);
    }

    private static ChoiceStatDelta choice(int row, int week, long epochMs) {
        return new ChoiceStatDelta(new ChoiceStatKey("guardian", "r1:ALL", "c" + row, "strike", "window", week,
                "outcome"), 1, 1, epochMs);
    }

    private static ValueStatDelta value(String tactic, String source, int week, long epochMs) {
        return new ValueStatDelta(new ValueStatKey("guardian", tactic, "v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE", source,
                "r1", week), 1, 0.5, 0.0, 0.5, 0.0, 0.0, 0, 0, epochMs);
    }

    private static void memory(Connection connection, UUID subject, long updatedEpochMs) throws Exception {
        PlayerMemorySql.apply(connection, new PlayerMemoryDelta(subject, new byte[] {1}, 1, updatedEpochMs, false));
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            return result.next() ? result.getLong(1) : -1L;
        }
    }
}
