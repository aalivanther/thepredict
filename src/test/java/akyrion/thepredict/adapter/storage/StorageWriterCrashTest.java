package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8 and JS-16: a child running a real writer is killed while it writes events and batches. The database still checks,
 * the last batch is all or nothing (the windows are exactly the batches applied) and every segment reads whole except,
 * at most, its last line. The kill lands at three different moments.
 */
class StorageWriterCrashTest {
    @TempDir
    Path directory;

    @Test
    void aKillInTheMiddleOfTheWorkLeavesACheckedDatabaseAndReadableSegments() throws Exception {
        for (var target : List.of(5L, 40L, 120L)) {
            var root = directory.resolve("kill-" + target);
            var records = root.resolve("records");
            var database = root.resolve("aggregates.db");
            killAfter(records, database, root.resolve("ready"), target);
            try (var connection = SqliteConnections.open(database, "normal", 2000)) {
                try (var statement = connection.createStatement();
                     var check = statement.executeQuery("PRAGMA quick_check")) {
                    assertTrue(check.next());
                    assertEquals("ok", check.getString(1));
                }
                var applied = scalar(connection, "SELECT last_applied_batch FROM runs WHERE run = '"
                        + StorageWriterCrashChild.RUN + "'");
                assertTrue(applied >= target, "batch " + applied + " of " + target);
                assertEquals(applied, scalar(connection, "SELECT windows FROM outcome_stats"),
                        "every applied batch is whole and none is half applied");
                assertEquals(0, scalar(connection, "SELECT clean_shutdown FROM runs"));
            }
            assertSegmentsRead(records);
        }
    }

    /** Every segment parses, and at most its last line, cut by the kill, is lost. */
    private static void assertSegmentsRead(Path records) throws Exception {
        try (var walk = Files.walk(records.resolve("events"))) {
            for (var file : walk.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList()) {
                var lines = List.of(Files.readString(file, StandardCharsets.UTF_8).split("\n", -1));
                var written = lines.stream().filter(line -> !line.isBlank()).count();
                var parsed = RecordReader.parse(new ArrayList<>(lines)).size();
                assertTrue(written - parsed <= 1, file + ": " + written + " lines, " + parsed + " read");
            }
        }
    }

    private static void killAfter(Path records, Path database, Path marker, long target) throws Exception {
        var executable = Path.of(System.getProperty("java.home"), "bin", "java");
        var process = new ProcessBuilder(executable.toString(), "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("java.class.path"), StorageWriterCrashChild.class.getName(), records.toString(),
                database.toString(), marker.toString(), Long.toString(target)).redirectErrorStream(true).start();
        var output = new StringBuilder();
        var deadline = System.nanoTime() + 30_000_000_000L;
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            while (System.nanoTime() < deadline && !Files.exists(marker)) {
                if (reader.ready()) {
                    output.append((char) reader.read());
                } else {
                    java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L);
                }
            }
            process.destroyForcibly();
            process.waitFor();
        }
        assertTrue(Files.exists(marker), output.toString());
    }

    private static long scalar(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
