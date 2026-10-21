package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.AnswerCacheDelta;
import akyrion.thepredict.engine.DecisionDailyDelta;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-27: the fingerprint and the stored model never carry a key (rules C8 and W6). */
class RunIdsTest {
    private static final String WITHOUT = "jev:\n  model: jev-1.13.0\n  timeoutMillis: 800\nrecords:\n  level: decisions\n";

    @TempDir
    Path directory;

    @Test
    void everyFormOfTheKeyEntryIsLeftOutOfTheFingerprint() {
        var expected = RunIds.configSha256(WITHOUT);
        assertEquals(expected, RunIds.configSha256(
                "jev:\n  apiKey: sk-secret\n  model: jev-1.13.0\n  timeoutMillis: 800\nrecords:\n  level: decisions\n"));
        assertEquals(expected, RunIds.configSha256(
                "jev:\n  apiKey : sk-secret\n  model: jev-1.13.0\n  timeoutMillis: 800\nrecords:\n  level: decisions\n"));
        assertEquals(expected, RunIds.configSha256(
                "jev:\n  \"apiKey\": sk-secret\n  model: jev-1.13.0\n  timeoutMillis: 800\nrecords:\n  level: decisions\n"));
        assertEquals(expected, RunIds.configSha256(
                "jev:\n  apiKey: |\n    sk-secret\n  model: jev-1.13.0\n  timeoutMillis: 800\nrecords:\n"
                        + "  level: decisions\n"));
        assertEquals(RunIds.configSha256("records:\n  level: decisions\n"),
                RunIds.configSha256("jev: {apiKey: sk-secret, model: jev-1.13.0}\nrecords:\n  level: decisions\n"));
        assertTrue(!expected.equals(RunIds.configSha256(WITHOUT.replace("800", "900"))));
        assertEquals(RunIds.configSha256("jev:\n  apiKeyEnvironment: TYPESAFE_API_KEY\n"),
                RunIds.sha256("jev:\n  apiKeyEnvironment: TYPESAFE_API_KEY\n".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void theDailyTablesKeepAVersionedModelAndThePlaceholderButNotAKeyShapedOne() throws Exception {
        var file = directory.resolve("daily.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            for (var model : java.util.List.of("sk-SECRETVALUE_123", "jev-1.13.0", "-")) {
                DecisionDailySql.upsert(connection, new DecisionDailyDelta("2026-10-14", model, "guardian", "full", 1,
                        1, 0, 400, 0, 0, 1, 0));
                AnswerCacheDailySql.upsert(connection, new AnswerCacheDelta("2026-10-14", model, "shadow", 1, 0, 0, 0,
                        Double.NaN));
            }
            assertEquals(java.util.List.of("-", RunIds.CUSTOM_MODEL, "jev-1.13.0"),
                    models(connection, "decision_daily"));
            assertEquals(java.util.List.of("-", RunIds.CUSTOM_MODEL, "jev-1.13.0"),
                    models(connection, "answer_cache_daily"));
        }
    }

    private static java.util.List<String> models(java.sql.Connection connection, String table) throws Exception {
        var models = new java.util.ArrayList<String>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT model FROM " + table + " ORDER BY model")) {
            while (result.next()) {
                models.add(result.getString(1));
            }
        }
        return models;
    }

    @Test
    void onlyAVersionedModelIsStoredWithTheRun() throws Exception {
        assertEquals("jev-1.13.0", RunIds.modelLabel("jev-1.13.0"));
        assertEquals("kev-1.0", RunIds.modelLabel("kev-1.0"));
        assertEquals(RunIds.CUSTOM_MODEL, RunIds.modelLabel("sk-SECRETVALUE_123"));
        var records = directory.resolve("records");
        Files.createDirectories(records);
        var database = directory.resolve("model.db");
        var base = WriterHosts.spec(records, database, false, true, 8, SegmentFiles.DISK, 8);
        var spec = new StorageWriter.Spec(base.directory(), base.database(), false, true, 8, base.segmentBytes(),
                base.segmentNanos(), false, 14, base.maxBytes(), "normal", 2000, 0, 7, base.run(), "0.0.1",
                "sk-SECRETVALUE_123", "abc", SegmentFiles.DISK, 8);
        var writer = new StorageWriter(spec, new Inbox<InboxEntry>(8));
        WriterHosts.until(() -> writer.health().state() == StorageState.READY);
        writer.close(3000);
        try (var connection = SqliteConnections.readOnly(database);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT model FROM runs")) {
            assertTrue(result.next());
            assertEquals(RunIds.CUSTOM_MODEL, result.getString(1));
        }
    }
}
