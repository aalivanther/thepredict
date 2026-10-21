package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryPolicyTest {
    @TempDir
    Path directory;

    @Test
    void theNewestBackupThatPassesQuickCheckIsRestoredAndTheCorruptOriginalStays() throws Exception {
        var database = directory.resolve("aggregates.db");
        var backups = directory.resolve("backups");
        Files.createDirectories(backups);
        var older = directory.resolve("older.db");
        var middle = directory.resolve("middle.db");
        seed(older, "old", backups);
        seed(middle, "mid", backups);
        Files.copy(older, backups.resolve("aggregates-20261006T000000Z.db"));
        Files.copy(middle, backups.resolve("aggregates-20261006T010000Z.db"));
        var broken = backups.resolve("aggregates-20261006T020000Z.db");
        Files.writeString(broken, "broken-newest");
        Files.writeString(database, "corrupt-live");
        Files.writeString(Path.of(database.toString() + "-wal"), "wal-bytes");
        Files.writeString(Path.of(database.toString() + "-shm"), "shm-bytes");
        assertFalse(RecoveryPolicy.quickCheck(broken));
        assertTrue(RecoveryPolicy.quickCheck(middle));
        assertEquals(RecoveryPolicy.Outcome.RESTORED, RecoveryPolicy.recover(database, backups));
        assertTrue(RecoveryPolicy.quickCheck(database));
        try (var connection = SqliteConnections.readOnly(database);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT run FROM runs")) {
            assertTrue(result.next());
            assertEquals("mid", result.getString(1));
            assertFalse(result.next());
        }
        var quarantined = corruptDatabases();
        assertEquals(1, quarantined.size());
        assertEquals("corrupt-live", Files.readString(quarantined.getFirst()));
        assertEquals("wal-bytes", Files.readString(Path.of(quarantined.getFirst().toString() + "-wal")));
        assertEquals("shm-bytes", Files.readString(Path.of(quarantined.getFirst().toString() + "-shm")));
        assertEquals("broken-newest", Files.readString(broken));
        var liveWal = Path.of(database.toString() + "-wal");
        assertTrue(!Files.exists(liveWal) || !Files.readString(liveWal).equals("wal-bytes"));
    }

    @Test
    void noGoodCopyLeavesTheLivePathAbsentAndKeepsTheCorruptFile() throws Exception {
        var database = directory.resolve("aggregates.db");
        var backups = directory.resolve("backups");
        Files.createDirectories(backups);
        Files.writeString(database, "corrupt-live");
        Files.writeString(backups.resolve("aggregates-20261006T020000Z.db"), "broken");
        assertEquals(RecoveryPolicy.Outcome.EMPTY, RecoveryPolicy.recover(database, backups));
        assertFalse(Files.exists(database));
        var quarantined = corruptDatabases();
        assertEquals(1, quarantined.size());
        assertEquals("corrupt-live", Files.readString(quarantined.getFirst()));
        assertEquals("broken", Files.readString(backups.resolve("aggregates-20261006T020000Z.db")));
    }

    @Test
    void anOldPreMigrationCopyIsTriedOnlyAfterTheNewerDatedCopies() throws Exception {
        var database = directory.resolve("aggregates.db");
        var backups = directory.resolve("dated-backups");
        Files.createDirectories(backups);
        var june = directory.resolve("june.db");
        var october = directory.resolve("october.db");
        var latest = directory.resolve("latest.db");
        seed(june, "june", backups);
        seed(october, "october", backups);
        seed(latest, "latest", backups);
        var premigration = backups.resolve("aggregates-v5.db");
        Files.copy(june, premigration);
        Files.setLastModifiedTime(premigration, FileTime.from(Instant.parse("2026-06-01T00:00:00Z")));
        Files.copy(october, backups.resolve("aggregates-20261010T000000Z.db"));
        Files.copy(latest, backups.resolve("aggregates-20261012T000000Z.db"));
        Files.writeString(database, "corrupt-live");
        assertEquals(RecoveryPolicy.Outcome.RESTORED, RecoveryPolicy.recover(database, backups));
        assertEquals("latest", run(database));
        assertEquals(List.of(backups.resolve("aggregates-20261012T000000Z.db"),
                backups.resolve("aggregates-20261010T000000Z.db"), premigration), BackupFiles.newestFirst(backups));
    }

    private static String run(Path database) throws Exception {
        try (var connection = SqliteConnections.readOnly(database);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT run FROM runs")) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private void seed(Path file, String run, Path backups) throws Exception {
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, backups);
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES (?, 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.setString(1, run);
                statement.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
    }

    private java.util.List<Path> corruptDatabases() throws Exception {
        var found = new ArrayList<Path>();
        try (var stream = Files.list(directory)) {
            for (var path : stream.toList()) {
                var name = path.getFileName().toString();
                if (name.startsWith("aggregates.corrupt-") && name.endsWith(".db")) {
                    found.add(path);
                }
            }
        }
        return found;
    }
}
