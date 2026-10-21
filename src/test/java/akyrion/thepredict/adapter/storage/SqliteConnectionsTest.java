package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConnectionsTest {
    @TempDir
    Path directory;

    @Test
    void pragmasComeBackInOrderAndJsonbExistsOnAtLeast345() throws Exception {
        var file = directory.resolve("aggregates.db");
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            var pragmas = SqlitePragmas.read(connection);
            assertEquals(List.of("journal_mode", "synchronous", "busy_timeout", "foreign_keys", "temp_store",
                    "wal_autocheckpoint", "cache_size"), List.copyOf(pragmas.keySet()));
            assertEquals("wal", pragmas.get("journal_mode"));
            assertEquals("1", pragmas.get("synchronous"));
            assertEquals("2000", pragmas.get("busy_timeout"));
            assertEquals("1", pragmas.get("foreign_keys"));
            assertEquals("1000", pragmas.get("wal_autocheckpoint"));
            assertEquals("-8192", pragmas.get("cache_size"));
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT jsonb('{\"a\":1}')")) {
                assertTrue(result.next());
            }
        }
        assertTrue(SqliteAvailability.compare(SqliteConnections.version(file), "3.45.0") >= 0);
    }
}
