package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-03 and JS-04: one dated name for every copy, time order, and a rotation that deletes only its own names. */
class BackupFilesTest {
    private static final Instant NOON = Instant.parse("2026-10-14T12:00:00Z");

    @TempDir
    Path directory;

    @Test
    void twoCopiesInOneSecondGetTwoNamesAndAPreMigrationCopyNamesItsVersion() throws Exception {
        var first = BackupFiles.next(directory, NOON);
        assertEquals("aggregates-20261014T120000Z.db", first.getFileName().toString());
        Files.writeString(first, "one");
        assertEquals("aggregates-20261014T120000Z-2.db", BackupFiles.next(directory, NOON).getFileName().toString());
        var premigration = BackupFiles.beforeMigration(directory, NOON, 5);
        assertEquals("aggregates-20261014T120000Z-v5.db", premigration.getFileName().toString());
        Files.writeString(premigration, "five");
        var again = BackupFiles.beforeMigration(directory, NOON, 5);
        assertEquals("aggregates-20261014T120000Z-2-v5.db", again.getFileName().toString());
        Files.writeString(again, "five again");
        BackupFiles.keepNewestBeforeMigration(directory, again, 5);
        assertFalse(Files.exists(premigration));
        assertTrue(Files.exists(again));
        assertTrue(Files.exists(first));
    }

    @Test
    void theOrderIsTheTimeOfTheCopyAndRotationKeepsOtherFiles() throws Exception {
        var legacy = copy("aggregates-v5.db", "2026-06-01T00:00:00Z");
        var old = copy("aggregates-20261001T000000Z.db", "2026-10-14T00:00:00Z");
        var mid = copy("aggregates-20261010T000000Z-v6.db", "2026-10-14T00:00:00Z");
        var newest = copy("aggregates-20261012T000000Z.db", "2026-01-01T00:00:00Z");
        var foreign = copy("manual-copy.db", "2020-01-01T00:00:00Z");
        assertEquals(List.of(newest, mid, old, legacy, foreign), BackupFiles.newestFirst(directory));
        assertEquals(2, BackupFiles.trim(directory, 2));
        assertEquals(List.of(newest, mid, foreign), BackupFiles.newestFirst(directory));
        assertEquals(0, BackupFiles.trim(directory, 2));
    }

    private Path copy(String name, String modified) throws Exception {
        var path = directory.resolve(name);
        Files.writeString(path, name);
        Files.setLastModifiedTime(path, FileTime.from(Instant.parse(modified)));
        return path;
    }
}
