package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigratorTest {
    @TempDir
    Path directory;

    @Test
    void anEmptyFileBecomesV1AndV2KeepsTheRowAfterACopy() throws Exception {
        var file = directory.resolve("aggregates.db");
        var backups = directory.resolve("records").resolve("backups");
        var first = SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 10L, backups);
        assertEquals(StorageState.READY, first.state());
        assertEquals(6, first.version());
        var second = new SchemaMigrator.Migration(7, "V007__probe.sql",
                "CREATE TABLE probe(id INTEGER PRIMARY KEY) STRICT", "probe");
        var migrations = new ArrayList<>(SchemaMigrator.bundled());
        migrations.add(second);
        var upgraded = SchemaMigrator.open(file, migrations, "normal", 2000, "0.0.1", 11L, backups);
        assertEquals(7, upgraded.version());
        try (var stream = Files.list(backups)) {
            assertTrue(stream.anyMatch(path -> path.getFileName().toString().endsWith(".db")));
        }
        try (var connection = SqliteConnections.open(file, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 1")) {
            assertEquals(1, result.getInt(1));
        }
    }

    @Test
    void aFailedMigrationStaysOnThePreviousVersion() throws Exception {
        var file = directory.resolve("fail.db");
        var backups = directory.resolve("fail-backups");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, backups);
        var broken = new SchemaMigrator.Migration(7, "V007__bad.sql", "NOT SQL AT ALL", "bad");
        var migrations = new ArrayList<>(SchemaMigrator.bundled());
        migrations.add(broken);
        var result = SchemaMigrator.open(file, migrations, "normal", 2000, "0.0.1", 2L, backups);
        assertEquals(StorageState.READ_ONLY, result.state());
        assertEquals("MIGRATION_FAILED", result.cause());
        try (var connection = SqliteConnections.readOnly(file)) {
            assertEquals(6, SchemaMigrator.userVersion(connection));
        }
    }

    @Test
    void aMigrationThatFailedTwiceStillAppliesOnceItIsFixed() throws Exception {
        var file = directory.resolve("retry.db");
        var backups = directory.resolve("retry-backups");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, backups);
        var broken = new ArrayList<>(SchemaMigrator.bundled());
        broken.add(new SchemaMigrator.Migration(7, "V007__bad.sql", "NOT SQL AT ALL", "bad"));
        assertEquals("MIGRATION_FAILED", SchemaMigrator.open(file, broken, "normal", 2000, "0.0.1", 2L, backups)
                .cause());
        assertEquals("MIGRATION_FAILED", SchemaMigrator.open(file, broken, "normal", 2000, "0.0.1", 3L, backups)
                .cause());
        var fixed = new ArrayList<>(SchemaMigrator.bundled());
        fixed.add(new SchemaMigrator.Migration(7, "V007__fixed.sql", "CREATE TABLE probe(id INTEGER PRIMARY KEY) STRICT",
                "fixed"));
        var result = SchemaMigrator.open(file, fixed, "normal", 2000, "0.0.1", 4L, backups);
        assertEquals(StorageState.READY, result.state());
        assertEquals(7, result.version());
        try (var stream = Files.list(backups)) {
            var copies = stream.map(path -> path.getFileName().toString()).toList();
            assertEquals(1, copies.size(), copies.toString());
            assertTrue(copies.getFirst().startsWith("aggregates-19700101T000000Z") && copies.getFirst().endsWith("-v6.db"),
                    copies.toString());
        }
    }

    @Test
    void aChangedChecksumOrANewerSchemaWritesNothing() throws Exception {
        var file = directory.resolve("same.db");
        var backups = directory.resolve("same-backups");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, backups);
        try (var connection = SqliteConnections.open(file, "normal", 2000);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
        var before = Files.readAllBytes(file);
        var bundled = SchemaMigrator.bundled();
        var lied = new SchemaMigrator.Migration(1, "V001__base.sql", bundled.getFirst().sql(), "nope");
        var pending = List.of(lied, bundled.get(1), bundled.get(2), bundled.get(3), bundled.get(4), bundled.get(5));
        var checksum = SchemaMigrator.open(file, pending, "normal", 2000, "0.0.1", 2L, backups);
        assertEquals("MIGRATION_CHECKSUM", checksum.cause());
        assertEquals(false, checksum.wrote());
        assertArrayEquals(before, Files.readAllBytes(file));
        try (var connection = SqliteConnections.open(file, "normal", 2000);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=9");
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
        var newerBefore = Files.readAllBytes(file);
        var newer = SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 3L, backups);
        assertEquals("SCHEMA_NEWER", newer.cause());
        assertArrayEquals(newerBefore, Files.readAllBytes(file));
    }
}
