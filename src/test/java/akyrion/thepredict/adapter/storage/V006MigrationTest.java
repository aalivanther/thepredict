package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V006MigrationTest {
    @TempDir
    Path directory;

    @Test
    void aV5CopyGainsTheDistillTables() throws Exception {
        var source = directory.resolve("v5.db");
        var opened = SchemaMigrator.open(source, SchemaMigrator.bundled().subList(0, 5), "normal", 2000, "0.0.1",
                1L, directory.resolve("backups"));
        assertEquals(5, opened.version());
        var copy = directory.resolve("copy.db");
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        var upgraded = SchemaMigrator.open(copy, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 2L,
                directory.resolve("copy-backups"));
        assertEquals(6, upgraded.version());
        try (var connection = SqliteConnections.open(copy, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT name, sql FROM sqlite_master WHERE name IN ('m29_choice_stats', 'm29_agreement_stats')")) {
            var joined = new StringBuilder();
            var names = 0;
            while (result.next()) {
                names++;
                joined.append(result.getString(1)).append('\n').append(result.getString(2)).append('\n');
            }
            assertEquals(2, names);
            assertTrue(joined.toString().contains("STRICT"));
            assertTrue(joined.toString().contains("WITHOUT ROWID"));
            assertTrue(joined.toString().contains("sum_delta"));
        }
    }
}
