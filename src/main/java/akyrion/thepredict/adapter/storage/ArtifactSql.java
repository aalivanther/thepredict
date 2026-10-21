package akyrion.thepredict.adapter.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Rows of {@code m28_artifacts} (M28.9, M29.6). The cap is 512 so a profile can keep a distilled artifact per regime.
 * The oldest row that is neither active nor the previous active is pruned.
 */
public final class ArtifactSql {
    /** Cap. Distilled and regime rows share this table. */
    public static final int CAP = 512;

    private ArtifactSql() {
    }

    /**
     * Inserts one artifact row.
     *
     * @param connection the connection
     * @param sha256 fingerprint
     * @param kind VALUE or CALIBRATION
     * @param bytes file length
     * @param entries cell count
     * @param batch source batch
     * @param epochMs wall time
     * @param state lifecycle state
     * @throws SQLException when the insert fails
     */
    public static void insert(Connection connection, String sha256, String kind, int bytes, int entries, int batch,
                              long epochMs, String state) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO m28_artifacts(sha256, kind, format_v, created_epoch_ms, source_batch, entries, bytes,
                  state, detail)
                VALUES (?,?,1,?,?,?,?,?,NULL)
                """)) {
            statement.setString(1, sha256);
            statement.setString(2, kind);
            statement.setLong(3, epochMs);
            statement.setInt(4, batch);
            statement.setInt(5, entries);
            statement.setInt(6, bytes);
            statement.setString(7, state);
            statement.executeUpdate();
        }
    }

    /**
     * Inserts one artifact row unless that fingerprint is already stored. A {@code VALUE} row records the format of
     * the {@code .tplv} the writer builds and accepts, {@value akyrion.thepredict.domain.learn.ValueArtifact#FORMAT}.
     *
     * @param connection the connection
     * @param sha256 fingerprint
     * @param kind VALUE or CALIBRATION
     * @param bytes file length
     * @param entries cell count
     * @param batch source batch
     * @param epochMs day source, from the rows, not a fresh clock read
     * @param state lifecycle state
     * @throws SQLException when the insert fails
     */
    public static void insertIgnore(Connection connection, String sha256, String kind, int bytes, int entries,
                                    int batch, long epochMs, String state) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO m28_artifacts(sha256, kind, format_v, created_epoch_ms, source_batch, entries,
                  bytes, state, detail)
                VALUES (?,?,?,?,?,?,?,?,NULL)
                """)) {
            statement.setString(1, sha256);
            statement.setString(2, kind);
            statement.setInt(3, "VALUE".equals(kind) ? akyrion.thepredict.domain.learn.ValueArtifact.FORMAT : 1);
            statement.setLong(4, epochMs);
            statement.setInt(5, batch);
            statement.setInt(6, entries);
            statement.setInt(7, bytes);
            statement.setString(8, state);
            statement.executeUpdate();
        }
    }

    /**
     * Fingerprints in creation order.
     *
     * @param connection the connection
     * @return the fingerprints
     * @throws SQLException when the query fails
     */
    public static List<String> list(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT sha256 FROM m28_artifacts ORDER BY created_epoch_ms, sha256")) {
            var rows = new ArrayList<String>();
            while (result.next()) {
                rows.add(result.getString(1));
            }
            return rows;
        }
    }

    /**
     * Deletes the oldest row that is not one of the two kept fingerprints, while the table is over the cap.
     *
     * @param connection the connection
     * @param active active sha, or empty
     * @param previous previous sha, or empty
     * @return rows deleted
     * @throws SQLException when the delete fails
     */
    public static int prune(Connection connection, String active, String previous) throws SQLException {
        var deleted = 0;
        while (count(connection) > CAP) {
            try (var statement = connection.prepareStatement("""
                    DELETE FROM m28_artifacts WHERE sha256 = (
                      SELECT sha256 FROM m28_artifacts
                      WHERE sha256 <> ? AND sha256 <> ?
                      ORDER BY created_epoch_ms ASC LIMIT 1)
                    """)) {
                statement.setString(1, active);
                statement.setString(2, previous);
                if (statement.executeUpdate() == 0) {
                    return deleted;
                }
                deleted++;
            }
        }
        return deleted;
    }

    private static int count(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM m28_artifacts")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
}
