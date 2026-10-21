package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictTablesTest {
    @TempDir
    Path directory;

    @Test
    void everyBundledTableIsStrictAndATextBlobIsRejected() throws Exception {
        var file = directory.resolve("aggregates.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        try (var connection = SqliteConnections.open(file, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table'")) {
            var tables = 0;
            while (result.next()) {
                tables++;
                assertTrue(result.getString(1).contains("STRICT"));
            }
            assertTrue(tables >= 5);
        }
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            assertThrows(SQLException.class, () -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO outcome_stats(profile, tactic, movement, level, context, windows, "
                            + "effect_mean, effect_m2, dealt_sum, taken_sum, reached, mob_died, target_died, casts, hits, "
                            + "first_epoch_ms, last_epoch_ms, extra) VALUES ('g','keep_distance','APPROACH','JEV',"
                            + "'v1:NONE:NONE', 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 'text')");
                }
            });
        }
    }
}
