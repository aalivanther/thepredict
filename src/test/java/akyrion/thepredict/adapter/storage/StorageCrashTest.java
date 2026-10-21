package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCrashTest {
    @TempDir
    Path directory;

    @Test
    void killingTheChildLeavesACleanCheckAndTheCommittedRow() throws Exception {
        var database = directory.resolve("aggregates.db");
        var marker = directory.resolve("ready");
        var jsonl = directory.resolve("events.jsonl");
        var executable = Path.of(System.getProperty("java.home"), "bin", "java");
        var process = new ProcessBuilder(executable.toString(), "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("java.class.path"), StorageCrashChild.class.getName(), database.toString(),
                marker.toString(), jsonl.toString()).redirectErrorStream(true).start();
        var output = new StringBuilder();
        var deadline = System.nanoTime() + 20_000_000_000L;
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            while (System.nanoTime() < deadline && !Files.exists(marker)) {
                if (reader.ready()) {
                    output.append((char) reader.read());
                } else {
                    java.util.concurrent.locks.LockSupport.parkNanos(5_000_000L);
                }
            }
        }
        assertTrue(Files.exists(marker), output.toString());
        process.destroyForcibly();
        process.waitFor();
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            try (var statement = connection.createStatement();
                 var check = statement.executeQuery("PRAGMA quick_check")) {
                assertTrue(check.next());
                assertEquals("ok", check.getString(1));
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT windows FROM outcome_stats")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getLong(1));
            }
            try (var statement = connection.createStatement();
                 var runs = statement.executeQuery("SELECT clean_shutdown FROM runs")) {
                assertTrue(runs.next());
                assertEquals(0, runs.getInt(1));
            }
        }
        var text = Files.readString(jsonl);
        assertTrue(text.contains("\"type\":\"planned\""));
        assertTrue(RecordReader.parse(java.util.List.of(text.split("\n", -1))).size() >= 1);
    }
}
