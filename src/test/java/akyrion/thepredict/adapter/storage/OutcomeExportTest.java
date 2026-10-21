package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeExportTest {
    @TempDir
    Path directory;

    @Test
    void aProfileExportsRfc4180Csv() throws Exception {
        var database = directory.resolve("aggregates.db");
        var records = directory.resolve("records");
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, records.resolve("backups"));
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO outcome_stats(profile, tactic, movement, level, context, windows, "
                        + "effect_mean, effect_m2, dealt_sum, taken_sum, reached, mob_died, target_died, casts, hits, "
                        + "first_epoch_ms, last_epoch_ms) VALUES ('guar,dian','keep_distance','APPROACH','JEV',"
                        + "'v1:NONE:NONE', 2, 0.5, 0.1, 0, 0, 1, 0, 0, 1, 1, 1, 2)");
            }
            var result = OutcomeExport.write(connection, records, "guar,dian");
            assertTrue(result.contains("outcomes 1"));
            var csv = Files.readString(records.resolve("exports").resolve("outcomes-guar_dian.csv"));
            assertTrue(csv.contains("\"guar,dian\""));
            assertTrue(csv.contains("\r\n"));
        }
    }
}
