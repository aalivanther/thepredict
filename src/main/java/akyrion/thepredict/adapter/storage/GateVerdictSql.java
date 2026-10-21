package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.StorageReply;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate verdicts in SQLite (M22.5). A later {@code decided_epoch_ms} replaces the row. A tie keeps the stored row.
 */
public final class GateVerdictSql {
    /** The upsert. A tie keeps the stored verdict. */
    public static final String UPSERT = """
            INSERT INTO m22_gate_verdicts(candidate_sha256, gate, verdict, run_id, decided_epoch_ms, detail)
            VALUES (?,?,?,?,?,NULL)
            ON CONFLICT(candidate_sha256, gate) DO UPDATE SET
              verdict = CASE WHEN excluded.decided_epoch_ms > decided_epoch_ms THEN excluded.verdict ELSE verdict END,
              run_id = CASE WHEN excluded.decided_epoch_ms > decided_epoch_ms THEN excluded.run_id ELSE run_id END,
              decided_epoch_ms = MAX(decided_epoch_ms, excluded.decided_epoch_ms)
            """;

    private GateVerdictSql() {
    }

    /**
     * Applies one verdict.
     *
     * @param connection the open transaction
     * @param row the verdict
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, StorageReply.GateVerdictRow row) throws SQLException {
        try (var statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, row.sha256());
            statement.setString(2, row.gate());
            statement.setString(3, row.verdict());
            statement.setString(4, row.runId());
            statement.setLong(5, row.decidedEpochMs());
            statement.executeUpdate();
        }
    }

    /**
     * Loads the newest verdicts.
     *
     * @param connection the connection
     * @param limit the cap, at least 1
     * @return the rows
     * @throws SQLException when the read fails
     */
    public static List<StorageReply.GateVerdictRow> load(Connection connection, int limit) throws SQLException {
        var cap = Math.max(1, Math.min(200, limit));
        try (var statement = connection.prepareStatement(
                "SELECT candidate_sha256, gate, verdict, run_id, decided_epoch_ms FROM m22_gate_verdicts "
                        + "ORDER BY decided_epoch_ms DESC LIMIT " + cap);
             var result = statement.executeQuery()) {
            var rows = new ArrayList<StorageReply.GateVerdictRow>();
            while (result.next()) {
                rows.add(new StorageReply.GateVerdictRow(result.getString(1), result.getString(2),
                        result.getString(3), result.getString(4), result.getLong(5)));
            }
            return List.copyOf(rows);
        }
    }
}
