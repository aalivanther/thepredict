package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.OutcomeStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeStatsSqlTest {
    @TempDir
    Path directory;

    @Test
    void aThousandSeededBatchesMatchTheJavaMergeIncludingPairs() throws Exception {
        var file = directory.resolve("aggregates.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var key = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                "v1:NEAR:PLAYER");
        var java = OutcomeStat.EMPTY;
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            for (var batch = 1; batch <= 1000; batch++) {
                var windows = batch % 17 == 0 ? 2 : 1;
                var stat = OutcomeStat.EMPTY;
                for (var window = 0; window < windows; window++) {
                    var word = StableRandom.draw(21L, batch, window);
                    var effect = ((word >>> 11) / (double) (1L << 53)) * 2.0 - 1.0;
                    stat = OutcomeStatMath.add(stat, effect, 0.01, 0.02, true, false, false, 1, 1);
                }
                java = OutcomeStatMath.merge(java, stat);
                BatchApply.apply(connection, new AggregateBatch(batch, List.of(new OutcomeStatDelta(key, stat, batch)),
                        List.of(), List.of()), "run");
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT windows, effect_mean, effect_m2 FROM outcome_stats")) {
                result.next();
                assertEquals(java.windows(), result.getLong(1));
                assertEquals(java.effectMean(), result.getDouble(2), 1e-9);
                assertEquals(java.effectM2(), result.getDouble(3), 1e-6);
            }
        }
    }

    @Test
    void aCappedLoadKeepsTheNewestRowsAndMarksTheExtraOne() throws Exception {
        var file = directory.resolve("capped.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            upsert(connection, "v1:OLD:A", 10);
            upsert(connection, "v1:NEW:A", 30);
            upsert(connection, "v1:MID:A", 20);
            var loaded = OutcomeStatsSql.load(connection, List.of(), 2);
            assertEquals(2, loaded.rows().size());
            assertTrue(loaded.truncated());
            assertEquals("v1:NEW:A", loaded.rows().get(0).key().context());
            assertEquals("v1:MID:A", loaded.rows().get(1).key().context());
        }
    }

    private static void upsert(java.sql.Connection connection, String context, long epoch) throws Exception {
        var key = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                context);
        OutcomeStatsSql.upsert(connection, new OutcomeStatDelta(key, OutcomeStat.EMPTY, epoch));
    }
}
