package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V005MigrationTest {
    @TempDir
    Path directory;

    @Test
    void aV4CopyGainsTheLearningTables() throws Exception {
        var source = directory.resolve("v4.db");
        var opened = SchemaMigrator.open(source, SchemaMigrator.bundled().subList(0, 4), "normal", 2000, "0.0.1",
                1L, directory.resolve("backups"));
        assertEquals(4, opened.version());
        var copy = directory.resolve("copy.db");
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        var upgraded = SchemaMigrator.open(copy, SchemaMigrator.bundled().subList(0, 5), "normal", 2000, "0.0.1", 2L,
                directory.resolve("copy-backups"));
        assertEquals(5, upgraded.version());
        try (var connection = SqliteConnections.open(copy, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT name, sql FROM sqlite_master WHERE name IN ('m28_value_stats', 'm28_artifacts')")) {
            var names = new java.util.HashSet<String>();
            while (result.next()) {
                names.add(result.getString(1));
                names.add(result.getString(2));
            }
            var joined = String.join("\n", names);
            assertTrue(joined.contains("m28_value_stats"));
            assertTrue(joined.contains("WITHOUT ROWID"));
            assertTrue(joined.contains("m28_artifacts"));
            assertTrue(joined.contains("sha256"));
        }
    }
}
