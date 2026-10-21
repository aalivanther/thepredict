package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.StorageState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens a database, checks applied checksums before any copy, and applies pending migrations (M21.5, errata B3).
 * {@code SCHEMA_NEWER} and {@code MIGRATION_CHECKSUM} leave the file byte-identical.
 */
public final class SchemaMigrator {
    private SchemaMigrator() {
    }

    /**
     * One forward migration.
     *
     * @param version version number, starting at 1
     * @param name resource name
     * @param sql statements separated by semicolons
     * @param sha256 checksum of the original bytes
     */
    public record Migration(int version, String name, String sql, String sha256) {
        /**
         * Loads a classpath resource {@code /storage/sqlite/<name>}.
         *
         * @param version version number
         * @param name file name
         * @return the migration
         */
        public static Migration resource(int version, String name) {
            try (var input = SchemaMigrator.class.getResourceAsStream("/storage/sqlite/" + name)) {
                if (input == null) {
                    throw new IllegalStateException("missing migration " + name);
                }
                var bytes = input.readAllBytes();
                return new Migration(version, name, new String(bytes, StandardCharsets.UTF_8), RunIds.sha256(bytes));
            } catch (IOException exception) {
                throw new IllegalStateException(name, exception);
            }
        }
    }

    /**
     * The outcome of an open.
     *
     * @param state store state
     * @param cause short cause, empty when ready
     * @param version applied user_version
     * @param wrote whether this call wrote the file
     */
    public record Result(StorageState state, String cause, int version, boolean wrote) {
    }

    /**
     * The bundled V001 migration.
     *
     * @return the migration
     */
    public static List<Migration> bundled() {
        return List.of(Migration.resource(1, "V001__base.sql"), Migration.resource(2, "V002__m22_eval.sql"),
                Migration.resource(3, "V003__m26_patterns.sql"), Migration.resource(4, "V004__m27_memory.sql"),
                Migration.resource(5, "V005__m28_learning.sql"),
                Migration.resource(6, "V006__m29_distill.sql"));
    }

    /**
     * Checks and migrates. Checksums are read with a read-only connection when the file already exists.
     *
     * @param file database file
     * @param migrations migrations in order, version 1 first
     * @param synchronous pragma
     * @param busyTimeoutMillis busy timeout
     * @param pluginVersion plugin version stored with each migration
     * @param epochMs wall time of the apply
     * @return the result
     */
    public static Result open(Path file, List<Migration> migrations, String synchronous, int busyTimeoutMillis,
                              String pluginVersion, long epochMs) {
        var parent = file.getParent() == null ? Path.of("records") : file.getParent().resolve("records");
        return open(file, migrations, synchronous, busyTimeoutMillis, pluginVersion, epochMs, parent.resolve("backups"));
    }

    /**
     * Checks and migrates, writing a pre-migration copy into {@code backupDirectory} (errata E21-3, EP-4).
     *
     * @param file database file
     * @param migrations migrations in order, version 1 first
     * @param synchronous pragma
     * @param busyTimeoutMillis busy timeout
     * @param pluginVersion plugin version stored with each migration
     * @param epochMs wall time of the apply
     * @param backupDirectory directory of the copy, inside {@code records/backups} in production
     * @return the result
     */
    public static Result open(Path file, List<Migration> migrations, String synchronous, int busyTimeoutMillis,
                              String pluginVersion, long epochMs, Path backupDirectory) {
        try {
            if (Files.exists(file) && Files.size(file) > 0) {
                var inspected = inspect(file, migrations);
                if (inspected != null) {
                    return inspected;
                }
            }
            Files.createDirectories(file.getParent());
            try (var connection = SqliteConnections.open(file, synchronous, busyTimeoutMillis)) {
                var current = userVersion(connection);
                var pending = new ArrayList<Migration>();
                for (var migration : migrations) {
                    if (migration.version() > current) {
                        pending.add(migration);
                    }
                }
                if (!pending.isEmpty() && current > 0) {
                    backup(connection, backupDirectory, current, epochMs);
                }
                for (var migration : pending) {
                    apply(connection, migration, pluginVersion, epochMs);
                }
                return new Result(StorageState.READY, "", userVersion(connection), !pending.isEmpty() || current == 0);
            }
        } catch (NoClassDefFoundError error) {
            return new Result(StorageState.DISABLED, "DRIVER_MISSING", 0, false);
        } catch (IOException exception) {
            return new Result(StorageState.READ_ONLY, "MIGRATION_FAILED", 0, false);
        } catch (SQLException exception) {
            return new Result(StorageState.READ_ONLY, failure(exception), 0, false);
        }
    }

    private static String failure(SQLException exception) {
        var code = exception.getErrorCode();
        var message = exception.getMessage() == null ? "" : exception.getMessage();
        if (code == 11 || message.contains("SQLITE_CORRUPT") || message.contains("malformed")) {
            return "SQLITE_CORRUPT";
        }
        if (code == 26 || message.contains("SQLITE_NOTADB") || message.contains("not a database")) {
            return "SQLITE_NOTADB";
        }
        return "MIGRATION_FAILED";
    }

    private static Result inspect(Path file, List<Migration> migrations) throws SQLException {
        try (var connection = SqliteConnections.readOnly(file)) {
            var current = userVersion(connection);
            var latest = migrations.isEmpty() ? 0 : migrations.getLast().version();
            if (current > latest) {
                return new Result(StorageState.READ_ONLY, "SCHEMA_NEWER", current, false);
            }
            if (current > 0 && !checksums(connection, migrations)) {
                return new Result(StorageState.READ_ONLY, "MIGRATION_CHECKSUM", current, false);
            }
            return null;
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("no such table")) {
                return null;
            }
            throw exception;
        }
    }

    private static boolean checksums(Connection connection, List<Migration> migrations) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT version, sha256 FROM schema_migrations")) {
            while (result.next()) {
                var version = result.getInt(1);
                var sha = result.getString(2);
                for (var migration : migrations) {
                    if (migration.version() == version && !migration.sha256().equals(sha)) {
                        return false;
                    }
                }
            }
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("no such table")) {
                return true;
            }
            throw exception;
        }
        return true;
    }

    /**
     * Copies the database before migrating away from {@code version} (M21.8). The name is free and dated, so a retry
     * after a failed migration never meets the file of the previous attempt, and only the newest copy of that version
     * is kept.
     */
    private static void backup(Connection connection, Path directory, int version, long epochMs)
            throws SQLException, IOException {
        Files.createDirectories(directory);
        var target = BackupFiles.beforeMigration(directory, java.time.Instant.ofEpochMilli(epochMs), version);
        BackupFiles.vacuumInto(connection, target);
        BackupFiles.keepNewestBeforeMigration(directory, target, version);
    }

    private static void apply(Connection connection, Migration migration, String pluginVersion, long epochMs)
            throws SQLException {
        connection.setAutoCommit(false);
        try {
            for (var statementText : migration.sql().split(";")) {
                var sql = statementText.trim();
                if (!sql.isEmpty()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, sha256, applied_epoch_ms, plugin_version) VALUES (?,?,?,?,?)")) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.name());
                statement.setString(3, migration.sha256());
                statement.setLong(4, epochMs);
                statement.setString(5, pluginVersion);
                statement.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version=" + migration.version());
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Reads {@code PRAGMA user_version}.
     *
     * @param connection the connection
     * @return the version
     * @throws SQLException when the pragma fails
     */
    public static int userVersion(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
}
