package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Recovers a database that failed {@code quick_check} (M21.8). The corrupt file is renamed, never deleted. A backup
 * is copied only after its own {@code quick_check}. With no good copy the live path is left absent so the caller can
 * open an empty database.
 */
public final class RecoveryPolicy {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private RecoveryPolicy() {
    }

    /** What {@link #recover} did with the live path. */
    public enum Outcome {
        /** A backup that passed {@code quick_check} is now the live file. */
        RESTORED,
        /** No backup passed. The live path is absent. */
        EMPTY
    }

    /**
     * Runs {@code PRAGMA quick_check} on a closed file.
     *
     * @param file the database
     * @return {@code true} when the pragma returns {@code ok}
     */
    public static boolean quickCheck(Path file) {
        try (var connection = SqliteConnections.readOnly(file);
             var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA quick_check")) {
            return result.next() && "ok".equalsIgnoreCase(result.getString(1));
        } catch (SQLException exception) {
            return false;
        }
    }

    /**
     * Sets the live file aside and copies the newest backup that passes {@code quick_check}. Copies are tried in the
     * time order of {@code BackupFiles.newestFirst}: the stamp in the name, or the modification time of a copy named
     * before that rule, so an old pre-migration copy is never tried before a newer periodic one.
     *
     * @param database the live database
     * @param backups the backup directory
     * @return {@link Outcome#RESTORED} or {@link Outcome#EMPTY}
     * @throws IOException when a rename or a copy fails
     */
    public static Outcome recover(Path database, Path backups) throws IOException {
        if (Files.exists(database)) {
            quarantine(database);
        }
        if (backups != null && Files.isDirectory(backups)) {
            for (var copy : BackupFiles.newestFirst(backups)) {
                if (!quickCheck(copy)) {
                    continue;
                }
                Files.copy(copy, database, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(Path.of(database.toString() + "-wal"));
                Files.deleteIfExists(Path.of(database.toString() + "-shm"));
                if (quickCheck(database)) {
                    return Outcome.RESTORED;
                }
                Files.deleteIfExists(database);
            }
        }
        Files.deleteIfExists(database);
        return Outcome.EMPTY;
    }

    private static void quarantine(Path database) throws IOException {
        var folder = database.getParent() == null ? Path.of(".") : database.getParent();
        var wal = Path.of(database.toString() + "-wal");
        var shm = Path.of(database.toString() + "-shm");
        var target = unique(folder, "aggregates.corrupt-" + STAMP.format(Instant.now()));
        Files.move(database, target);
        moveIfPresent(wal, Path.of(target.toString() + "-wal"));
        moveIfPresent(shm, Path.of(target.toString() + "-shm"));
    }

    private static Path unique(Path folder, String stem) {
        var candidate = folder.resolve(stem + ".db");
        var index = 2;
        while (Files.exists(candidate)) {
            candidate = folder.resolve(stem + "-" + index + ".db");
            index++;
        }
        return candidate;
    }

    private static void moveIfPresent(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.move(source, target);
        }
    }
}
