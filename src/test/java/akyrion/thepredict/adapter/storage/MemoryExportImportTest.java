package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.engine.PlayerMemoryDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryExportImportTest {
    @TempDir
    Path directory;

    @Test
    void exportThenImportRoundTripsAndAParentPathIsRejected() throws Exception {
        var file = directory.resolve("aggregates.db");
        var records = directory.resolve("records");
        Files.createDirectories(records.resolve("imports"));
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, records.resolve("backups"));
        var subject = new UUID(3, 4);
        var bytes = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes();
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(subject, bytes, 0, 50, false));
            var exported = MemoryAdmin.export(connection, records, "all");
            assertTrue(exported.startsWith("exported 1"));
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(subject, new byte[0], 0, 51, true));
            var name = exported.substring(exported.lastIndexOf(' ') + 1);
            Files.copy(records.resolve("exports").resolve(name), records.resolve("imports").resolve(name));
            var imported = MemoryAdmin.importFile(connection, records, name, false);
            assertEquals("imported 1", imported);
            assertEquals(1, PlayerMemorySql.count(connection));
            assertThrows(Exception.class, () -> MemoryAdmin.importFile(connection, records, "../secret.jsonl", true));
            var many = new UUID(9, 1);
            for (var index = 0; index < 10_000; index++) {
                var id = new UUID(9, index + 2L);
                PlayerMemorySql.apply(connection, new PlayerMemoryDelta(id, bytes, index, 100 + index, false));
            }
            PlayerMemorySql.apply(connection, new PlayerMemoryDelta(many, bytes, 1, 50, false));
            var selected = MemoryAdmin.export(connection, records, many.toString());
            assertTrue(selected.startsWith("exported 1"));
            var rows = PlayerMemorySql.count(connection);
            assertTrue(rows >= 10_000);
            var all = MemoryAdmin.export(connection, records, "all");
            assertTrue(all.startsWith("exported " + rows));
            var fileName = all.substring(all.lastIndexOf(' ') + 1);
            var lines = Files.readAllLines(records.resolve("exports").resolve(fileName));
            assertEquals(rows, lines.size());
            var good = lines.getFirst();
            var newer = good.replace("\"record_v\":1", "\"record_v\":2");
            var badUuid = good.replaceFirst("\"subject\":\"[^\"]+\"", "\"subject\":\"not-a-uuid\"");
            var fat = "{\"v\":1,\"subject\":\"" + new UUID(9, 8) + "\",\"record_v\":1,\"updated_epoch_ms\":11,"
                    + "\"record_b64\":\"" + java.util.Base64.getEncoder().encodeToString(new byte[1025])
                    + "\",\"decoded\":\"unread\"}";
            var version = "{\"v\":1,\"subject\":\"" + new UUID(9, 9) + "\",\"record_v\":1,\"updated_epoch_ms\":9,"
                    + "\"record_b64\":\"" + java.util.Base64.getEncoder().encodeToString(new byte[] {2, 16, 25, 0})
                    + "\",\"decoded\":\"unread\"}";
            var mixed = records.resolve("imports").resolve("mixed.jsonl");
            Files.writeString(mixed, good + "\n" + newer + "\n" + badUuid + "\n" + fat + "\n" + version + "\n");
            var before = PlayerMemorySql.count(connection);
            var mixedResult = MemoryAdmin.importFile(connection, records, "mixed.jsonl", true);
            assertTrue(mixedResult.contains("rejected"));
            assertTrue(mixedResult.contains("newer"));
            assertTrue(mixedResult.contains("uuid"));
            assertTrue(mixedResult.contains("bytes"));
            assertTrue(PlayerMemorySql.count(connection) >= before);
        }
    }
}
