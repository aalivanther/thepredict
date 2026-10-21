package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V004MigrationTest {
    @TempDir
    Path directory;

    @Test
    void aV3CopyGainsTheMemoryTable() throws Exception {
        var source = directory.resolve("v3.db");
        var v3 = SchemaMigrator.bundled().subList(0, 3);
        var opened = SchemaMigrator.open(source, v3, "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        assertEquals(3, opened.version());
        var copy = directory.resolve("copy.db");
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        var upgraded = SchemaMigrator.open(copy, SchemaMigrator.bundled().subList(0, 5), "normal", 2000, "0.0.1", 2L,
                directory.resolve("copy-backups"));
        assertEquals(5, upgraded.version());
        try (var connection = SqliteConnections.open(copy, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE name = 'm27_player_memory'")) {
            assertTrue(result.next());
            var sql = result.getString(1);
            assertTrue(sql.contains("STRICT"));
            assertTrue(sql.contains("length(subject) = 16"));
            assertTrue(sql.contains("length(record) <= 1024"));
            assertTrue(sql.contains("record_v INTEGER NOT NULL"));
            assertTrue(!sql.contains("WITHOUT ROWID"));
        }
    }
}
