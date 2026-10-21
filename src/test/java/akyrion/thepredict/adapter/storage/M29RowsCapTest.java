package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 513 artifact rows prune to 512. A cap of 64 would fail this count. */
class M29RowsCapTest {
    @TempDir
    Path directory;

    @Test
    void pruneKeepsFiveHundredTwelveRows() throws Exception {
        var database = directory.resolve("m29.db");
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L,
                directory.resolve("backups"));
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            for (var row = 0; row < 513; row++) {
                ArtifactSql.insert(connection, sha(row), "DISTILLED", 8, 1, 1, row, "READY");
            }
            ArtifactSql.prune(connection, "", "");
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM m28_artifacts")) {
                assertTrueCount(result);
            }
        }
    }

    private static void assertTrueCount(java.sql.ResultSet result) throws Exception {
        result.next();
        assertEquals(512, result.getInt(1));
        assertEquals(512, ArtifactSql.CAP);
    }

    private static String sha(int row) {
        return String.format("%064x", row + 1L);
    }
}
