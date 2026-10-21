package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.StorageState;

import java.sql.SQLException;

/**
 * Decides whether aggregates can open (M21.4). A missing driver or a SQLite older than 3.45 disables them. The JSONL
 * writer is unaffected.
 */
public final class SqliteAvailability {
    /** Oldest SQLite that stores JSONB in a STRICT BLOB column. */
    public static final String MINIMUM = "3.45.0";

    private SqliteAvailability() {
    }

    /**
     * What {@link #assess} found.
     *
     * @param state the store state
     * @param cause short cause, empty when ready
     * @param version the version read, or empty
     */
    public record Assessment(StorageState state, String cause, String version) {
    }

    /**
     * Opens nothing itself. The probe loads the driver and returns {@code sqlite_version()}.
     *
     * @param probe the probe
     * @return the assessment
     */
    public static Assessment assess(Probe probe) {
        try {
            return classify(probe.version());
        } catch (NoClassDefFoundError error) {
            return new Assessment(StorageState.DISABLED, "DRIVER_MISSING", "");
        } catch (SQLException exception) {
            return new Assessment(StorageState.FAILED, exception.getClass().getSimpleName(), "");
        }
    }

    /**
     * Classifies a version string without a connection.
     *
     * @param version {@code sqlite_version()}
     * @return ready at or above {@link #MINIMUM}, otherwise disabled
     */
    public static Assessment classify(String version) {
        if (version == null || version.isBlank() || compare(version, MINIMUM) < 0) {
            return new Assessment(StorageState.DISABLED, "SQLITE_TOO_OLD", version == null ? "" : version);
        }
        return new Assessment(StorageState.READY, "", version);
    }

    /**
     * Compares dotted numeric versions.
     *
     * @param left one version
     * @param right the other
     * @return negative when {@code left} is older
     */
    public static int compare(String left, String right) {
        var a = left.split("\\.");
        var b = right.split("\\.");
        var count = Math.max(a.length, b.length);
        for (var index = 0; index < count; index++) {
            var first = index < a.length ? number(a[index]) : 0;
            var second = index < b.length ? number(b[index]) : 0;
            if (first != second) {
                return Integer.compare(first, second);
            }
        }
        return 0;
    }

    private static int number(String token) {
        var digits = new StringBuilder();
        for (var index = 0; index < token.length(); index++) {
            var character = token.charAt(index);
            if (character < '0' || character > '9') {
                break;
            }
            digits.append(character);
        }
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
    }

    /** Reads {@code sqlite_version()} or throws. */
    @FunctionalInterface
    public interface Probe {
        /**
         * Returns the version.
         *
         * @return {@code sqlite_version()}
         * @throws SQLException when the database cannot be opened
         */
        String version() throws SQLException;
    }
}
