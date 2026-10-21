package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.PatternStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternStatsSqlTest {
    @TempDir
    Path directory;

    @Test
    void twoRowsMergeWithChanAndKeepTheEarlierEpoch() throws Exception {
        var file = directory.resolve("patterns.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            PatternStatsSql.upsert(connection, row(1, 0.0, 10));
            PatternStatsSql.upsert(connection, row(1, 2.0, 30));
            var loaded = PatternStatsQuery.ofProfile(PatternStatsSql.load(connection), "guardian");
            assertEquals(1, loaded.size());
            assertEquals(2, loaded.getFirst().encounters());
            assertEquals(2, loaded.getFirst().counterWindows());
            assertEquals(1.0, loaded.getFirst().counterEffectMean(), 1e-9);
            assertEquals(10L, loaded.getFirst().firstEpochMs());
            assertEquals(30L, loaded.getFirst().lastEpochMs());
        }
    }

    @Test
    void learnedExtraDoesNotMoveTheCounterMean() throws Exception {
        var file = directory.resolve("learned.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var delta = new PatternStatDelta("guardian", "spams_clicks", "player", "bias", 1, 0, 1, 0, 2, 0.25, 0.0, 5L,
                5L);
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            var batch = new AggregateBatch(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(delta),
                    List.of());
            assertTrue(BatchApply.apply(connection, batch, "run"));
            assertLearned(connection, 0.25);
            PatternStatsSql.noteLearned(connection, delta.profile(), delta.pattern(), delta.targetKind(), delta.mode(),
                    PatternStatsSql.learnedPayload(delta, "landed"));
            assertLearned(connection, 0.25);
        }
    }

    private static void assertLearned(java.sql.Connection connection, double mean) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT counter_effect_mean, extra FROM m26_pattern_stats")) {
            assertTrue(result.next());
            assertEquals(mean, result.getDouble(1), 1.0e-9);
            var extra = new String(result.getBytes(2), StandardCharsets.UTF_8);
            assertTrue(extra.contains("\"learned\""));
            assertTrue(!extra.contains("counter_effect_mean="));
        }
    }

    private static PatternStatDelta row(long windows, double mean, long epoch) {
        return new PatternStatDelta("guardian", "spams_clicks", "player", "observe", 1, 1, 0, 0, windows, mean, 0.0,
                epoch, epoch);
    }
}
