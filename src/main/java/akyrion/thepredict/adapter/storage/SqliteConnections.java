package akyrion.thepredict.adapter.storage;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The two connections of the writer thread (M21.4). No pool and no {@code DriverManager}. A read-only open uses
 * {@link SQLiteConfig#setReadOnly(boolean)} so a checksum check does not write the file.
 */
public final class SqliteConnections {
    private SqliteConnections() {
    }

    /**
     * Opens a writable connection and applies the pragmas.
     *
     * @param file the database file
     * @param synchronous {@code normal} or {@code full}
     * @param busyTimeoutMillis busy timeout
     * @return the connection; the caller closes it
     * @throws SQLException when the file cannot be opened
     */
    public static Connection open(Path file, String synchronous, int busyTimeoutMillis) throws SQLException {
        var source = new SQLiteDataSource(new SQLiteConfig());
        source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        var connection = source.getConnection();
        SqlitePragmas.apply(connection, synchronous, busyTimeoutMillis);
        return connection;
    }

    /**
     * Opens a read-only connection. It does not apply write pragmas.
     *
     * @param file the database file
     * @return the connection
     * @throws SQLException when the file cannot be opened
     */
    public static Connection readOnly(Path file) throws SQLException {
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        var source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        return source.getConnection();
    }

    /**
     * Reads {@code sqlite_version()} from a new writable connection and closes it. Used by availability.
     *
     * @param file the database file
     * @return the version
     * @throws SQLException when the file cannot be opened
     */
    public static String version(Path file) throws SQLException {
        try (var connection = open(file, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery("select sqlite_version()")) {
            result.next();
            return result.getString(1);
        }
    }
}
