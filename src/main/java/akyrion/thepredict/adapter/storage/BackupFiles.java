package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Names, order and rotation of the database copies in {@code records/backups} (M21.8). A periodic or manual copy is
 * {@code aggregates-<yyyyMMdd'T'HHmmss'Z'>.db} and the copy before a migration adds {@code -v<version>}; a second copy
 * in the same second adds {@code -<n>} first. The name orders copies by time, so recovery tries the newest first and
 * rotation drops the oldest. A copy named before this rule ({@code aggregates-v<N>.db}) and any other {@code .db} file
 * are ordered by their modification time. Rotation deletes only the names above, never another file. Only the
 * storage thread calls this.
 */
final class BackupFiles {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Pattern STAMPED = Pattern.compile(
            "aggregates-(\\d{8}T\\d{6}Z)(?:-(\\d{1,6}))?(?:-v(\\d{1,6}))?\\.db");
    private static final Pattern LEGACY = Pattern.compile("aggregates-v\\d{1,6}\\.db");

    private BackupFiles() {
    }

    /**
     * A free name for a periodic or manual copy.
     *
     * @param directory the backup directory
     * @param now wall time of the copy
     * @return a path that does not exist yet
     */
    static Path next(Path directory, Instant now) {
        return free(directory, "aggregates-" + STAMP.format(now), "");
    }

    /**
     * A free name for the copy taken before migrating away from {@code version}.
     *
     * @param directory the backup directory
     * @param now wall time of the copy
     * @param version the schema version copied
     * @return a path that does not exist yet
     */
    static Path beforeMigration(Path directory, Instant now, int version) {
        return free(directory, "aggregates-" + STAMP.format(now), "-v" + version);
    }

    /**
     * Writes a consistent copy of the open database with {@code VACUUM INTO}. The target must not exist.
     *
     * @param connection an open connection
     * @param target the new file
     * @throws SQLException when the copy fails
     */
    static void vacuumInto(Connection connection, Path target) throws SQLException {
        var quoted = target.toAbsolutePath().toString().replace("'", "''");
        try (var statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + quoted + "'");
        }
    }

    /**
     * Deletes the other copies taken before migrating away from the same version, so one per version is left.
     *
     * @param directory the backup directory
     * @param kept the copy just written
     * @param version the schema version of that copy
     * @throws IOException when the directory cannot be listed or a file cannot be deleted
     */
    static void keepNewestBeforeMigration(Path directory, Path kept, int version) throws IOException {
        for (var copy : candidates(directory)) {
            var matcher = STAMPED.matcher(copy.getFileName().toString());
            if (matcher.matches() && String.valueOf(version).equals(matcher.group(3)) && !copy.equals(kept)) {
                Files.deleteIfExists(copy);
            }
        }
    }

    /**
     * Every {@code .db} file of the directory, newest first.
     *
     * @param directory the backup directory
     * @return the copies; empty when the directory is absent
     * @throws IOException when the directory cannot be listed
     */
    static List<Path> newestFirst(Path directory) throws IOException {
        var dated = new ArrayList<Dated>();
        for (var copy : candidates(directory)) {
            dated.add(dated(copy));
        }
        dated.sort(Comparator.comparingLong(Dated::millis).thenComparingInt(Dated::index)
                .thenComparing(Dated::name).reversed());
        var ordered = new ArrayList<Path>(dated.size());
        for (var copy : dated) {
            ordered.add(copy.path());
        }
        return ordered;
    }

    /**
     * Deletes the oldest copies this class names until {@code kept} are left. Other files are not counted.
     *
     * @param directory the backup directory
     * @param kept copies kept, at least 1
     * @return files deleted
     * @throws IOException when the directory cannot be listed or a file cannot be deleted
     */
    static int trim(Path directory, int kept) throws IOException {
        var owned = 0;
        var deleted = 0;
        for (var copy : newestFirst(directory)) {
            if (!owned(copy.getFileName().toString())) {
                continue;
            }
            owned++;
            if (owned > Math.max(1, kept) && Files.deleteIfExists(copy)) {
                deleted++;
            }
        }
        return deleted;
    }

    private static boolean owned(String name) {
        return STAMPED.matcher(name).matches() || LEGACY.matcher(name).matches();
    }

    private static List<Path> candidates(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".db") && Files.isRegularFile(path))
                    .toList();
        }
    }

    private static Dated dated(Path copy) throws IOException {
        var name = copy.getFileName().toString();
        var matcher = STAMPED.matcher(name);
        if (matcher.matches()) {
            var index = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            return new Dated(copy, name, stampMillis(matcher.group(1)), index);
        }
        return new Dated(copy, name, Files.getLastModifiedTime(copy).toMillis(), 0);
    }

    /** The time of a stamp; a stamp that is not a real date, such as day 00, is the oldest. */
    private static long stampMillis(String stamp) {
        try {
            return Instant.from(STAMP.parse(stamp)).toEpochMilli();
        } catch (DateTimeException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static Path free(Path directory, String stem, String suffix) {
        var candidate = directory.resolve(stem + suffix + ".db");
        for (var index = 2; Files.exists(candidate); index++) {
            candidate = directory.resolve(stem + "-" + index + suffix + ".db");
        }
        return candidate;
    }

    private record Dated(Path path, String name, long millis, int index) {
    }
}
