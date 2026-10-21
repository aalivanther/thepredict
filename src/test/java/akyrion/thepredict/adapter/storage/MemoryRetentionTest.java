package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.PlayerMemoryDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryRetentionTest {
    @TempDir
    Path directory;

    @Test
    void purgeDeletesOldRowsInChunksOf1000() throws Exception {
        var file = directory.resolve("aggregates.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            for (var index = 0; index < 1001; index++) {
                PlayerMemorySql.apply(connection, new PlayerMemoryDelta(new UUID(0, index), new byte[] {1}, 0, 1,
                        false));
            }
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(new UUID(9, 9), new byte[] {1}, 0, 5_000_000_000L,
                    false));
            var removed = 0;
            int chunk;
            do {
                chunk = PlayerMemorySql.purge(connection, 1_000, 1000);
                removed += chunk;
            } while (chunk == 1000);
            assertEquals(1001, removed);
            assertEquals(1, PlayerMemorySql.count(connection));
        }
    }
}
