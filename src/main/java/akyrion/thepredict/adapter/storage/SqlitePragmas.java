package akyrion.thepredict.adapter.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies and reads back the PRAGMAs of M21.4, in the documented order. {@code synchronous=NORMAL} reads back as 1.
 */
public final class SqlitePragmas {
    private SqlitePragmas() {
    }

    /**
     * Applies the pragmas.
     *
     * @param connection an open connection
     * @param synchronous {@code normal} or {@code full}
     * @param busyTimeoutMillis busy timeout
     * @throws SQLException when a pragma fails
     */
    public static void apply(Connection connection, String synchronous, int busyTimeoutMillis) throws SQLException {
        var mode = synchronous.equals("full") ? "FULL" : "NORMAL";
        execute(connection, "PRAGMA journal_mode=WAL");
        execute(connection, "PRAGMA synchronous=" + mode);
        execute(connection, "PRAGMA busy_timeout=" + busyTimeoutMillis);
        execute(connection, "PRAGMA foreign_keys=ON");
        execute(connection, "PRAGMA temp_store=MEMORY");
        execute(connection, "PRAGMA wal_autocheckpoint=1000");
        execute(connection, "PRAGMA cache_size=-8192");
    }

    /**
     * Reads the pragmas back.
     *
     * @param connection an open connection
     * @return name to value, in the apply order
     * @throws SQLException when a read fails
     */
    public static Map<String, String> read(Connection connection) throws SQLException {
        var values = new LinkedHashMap<String, String>();
        values.put("journal_mode", query(connection, "PRAGMA journal_mode"));
        values.put("synchronous", query(connection, "PRAGMA synchronous"));
        values.put("busy_timeout", query(connection, "PRAGMA busy_timeout"));
        values.put("foreign_keys", query(connection, "PRAGMA foreign_keys"));
        values.put("temp_store", query(connection, "PRAGMA temp_store"));
        values.put("wal_autocheckpoint", query(connection, "PRAGMA wal_autocheckpoint"));
        values.put("cache_size", query(connection, "PRAGMA cache_size"));
        return values;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String query(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : "";
        }
    }
}
