package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.AnswerCacheDelta;
import akyrion.thepredict.engine.DifficultyStatDelta;
import akyrion.thepredict.engine.OutcomeStatDelta;
import akyrion.thepredict.engine.PatternStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JS-09 and JS-21: a delta of no window keeps the batch in each of the four tables that merge a mean (outcome, value,
 * difficulty, pattern), and a delta without a percentile keeps the stored one.
 */
class ZeroWindowUpsertTest {
    @TempDir
    Path directory;

    @Test
    void twoEmptyOutcomeDeltasOnOneKeyApplyAndKeepTheMean() throws Exception {
        try (var connection = database()) {
            insertRun(connection);
            var key = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH,
                    ControlLevel.JEV, "v1:NONE:NONE");
            assertTrue(BatchApply.apply(connection, new AggregateBatch(1, List.of(new OutcomeStatDelta(key,
                    OutcomeStat.EMPTY, 1L), new OutcomeStatDelta(key, OutcomeStat.EMPTY, 2L)), List.of(), List.of()),
                    "run"));
            assertEquals(0L, scalar(connection, "SELECT windows FROM outcome_stats"));
            assertEquals(1L, scalar(connection, "SELECT last_applied_batch FROM runs"));
            var week = ValueStatKey.week(System.currentTimeMillis());
            var valueKey = new ValueStatKey("guardian", "strike", "v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE", "sim", "r1",
                    week);
            var empty = new ValueStatDelta(valueKey, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 1L);
            assertTrue(BatchApply.apply(connection, new AggregateBatch(2, List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(empty, empty)), "run"));
            assertEquals(2L, scalar(connection, "SELECT last_applied_batch FROM runs"));
        }
    }

    @Test
    void difficultyAndPatternDeltasWithoutAWindowApplyAndKeepTheirMeans() throws Exception {
        try (var connection = database()) {
            insertRun(connection);
            var difficulty = new DifficultyStatDelta("guardian", "v1:player:1:NORMAL", 4, 0, 0.0, 0.0, 0, 1, 4, 1L,
                    1L, 1L);
            var pattern = new PatternStatDelta("guardian", "spams_clicks", "player", "observe", 1, 1, 0, 0, 0, 0.0,
                    0.0, 1L, 1L);
            assertTrue(BatchApply.apply(connection, new AggregateBatch(1, List.of(), List.of(), List.of(),
                    List.of(difficulty, difficulty), List.of(), List.of(pattern, pattern)), "run"));
            assertEquals(2L, scalar(connection, "SELECT excluded FROM m22_difficulty_stats"));
            assertEquals(0L, scalar(connection, "SELECT fights FROM m22_difficulty_stats"));
            assertEquals(2L, scalar(connection, "SELECT encounters FROM m26_pattern_stats"));
            assertEquals(0L, scalar(connection, "SELECT counter_windows FROM m26_pattern_stats"));
        }
    }

    @Test
    void aDeltaWithoutAPercentileKeepsTheStoredOne() throws Exception {
        try (var connection = database()) {
            AnswerCacheDailySql.upsert(connection, new AnswerCacheDelta("2026-10-14", "jev-1.13.0", "shadow", 4, 1, 2,
                    1, 0.3));
            AnswerCacheDailySql.upsert(connection, new AnswerCacheDelta("2026-10-14", "jev-1.13.0", "shadow", 2, 0, 0,
                    0, Double.NaN));
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT lookups, confidence_delta_p95 FROM answer_cache_daily")) {
                assertTrue(result.next());
                assertEquals(6, result.getLong(1));
                assertEquals(0.3, result.getDouble(2), 1e-12);
            }
        }
    }

    private Connection database() throws Exception {
        var file = directory.resolve("zero.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        return SqliteConnections.open(file, "normal", 2000);
    }

    private static void insertRun(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, "
                    + "config_sha256, sqlite_version) VALUES ('run', 1, 0, '0.0.1', 'jev-1.13.0', 'abc', '3.49.1')");
        }
    }

    private static long scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
