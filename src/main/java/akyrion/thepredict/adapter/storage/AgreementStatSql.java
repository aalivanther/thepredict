package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.distill.AgreementStatDelta;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Upsert and pages of {@code m29_agreement_stats} (M29.1, errata E29-1 applied). {@code TableUpkeep} keeps its cap.
 */
public final class AgreementStatSql {
    /** Row cap of A5. The writer prunes above it, see {@code TableUpkeep}. */
    public static final int CAP = 100_000;
    /** The upsert. */
    public static final String UPSERT = """
            INSERT INTO m29_agreement_stats(artifact, profile, regime, context, week, n, sum_delta, sum_delta_sq,
              compared, agreed, audited, audit_agreed, last_epoch_ms)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(artifact, profile, regime, context, week) DO UPDATE SET
              n = n + excluded.n,
              sum_delta = sum_delta + excluded.sum_delta,
              sum_delta_sq = sum_delta_sq + excluded.sum_delta_sq,
              compared = compared + excluded.compared,
              agreed = agreed + excluded.agreed,
              audited = audited + excluded.audited,
              audit_agreed = audit_agreed + excluded.audit_agreed,
              last_epoch_ms = MAX(last_epoch_ms, excluded.last_epoch_ms)
            """;

    private AgreementStatSql() {
    }

    /**
     * Applies one delta.
     *
     * @param connection the open transaction
     * @param delta the delta
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, AgreementStatDelta delta) throws SQLException {
        try (var statement = connection.prepareStatement(UPSERT)) {
            bind(statement, delta);
            statement.executeUpdate();
        }
    }

    /**
     * Binds one delta to a prepared {@code UPSERT}, so a batch prepares the statement once (JS-25).
     *
     * @param statement the prepared upsert
     * @param delta the delta
     * @throws SQLException when a value cannot be bound
     */
    static void bind(java.sql.PreparedStatement statement, AgreementStatDelta delta) throws SQLException {
        statement.setString(1, delta.artifact());
        statement.setString(2, delta.profile());
        statement.setString(3, delta.regime());
        statement.setString(4, delta.context());
        statement.setInt(5, delta.week());
        statement.setInt(6, delta.n());
        statement.setDouble(7, delta.sumDelta());
        statement.setDouble(8, delta.sumDeltaSq());
        statement.setInt(9, delta.compared());
        statement.setInt(10, delta.agreed());
        statement.setInt(11, delta.audited());
        statement.setInt(12, delta.auditAgreed());
        statement.setLong(13, delta.epochMs());
    }

    /**
     * One page, ordered by week.
     *
     * @param connection the open connection
     * @param artifact artifact filter, empty for every artifact
     * @param limit page size
     * @return the rows
     * @throws SQLException when the query fails
     */
    public static List<AgreementStatDelta> page(Connection connection, String artifact, int limit) throws SQLException {
        var sql = artifact.isEmpty()
                ? "SELECT artifact, profile, regime, context, week, n, sum_delta, sum_delta_sq, compared, agreed,"
                + " audited, audit_agreed, last_epoch_ms FROM m29_agreement_stats ORDER BY week LIMIT ?"
                : "SELECT artifact, profile, regime, context, week, n, sum_delta, sum_delta_sq, compared, agreed,"
                + " audited, audit_agreed, last_epoch_ms FROM m29_agreement_stats WHERE artifact = ?"
                + " ORDER BY week LIMIT ?";
        var rows = new ArrayList<AgreementStatDelta>();
        try (var statement = connection.prepareStatement(sql)) {
            if (artifact.isEmpty()) {
                statement.setInt(1, limit);
            } else {
                statement.setString(1, artifact);
                statement.setInt(2, limit);
            }
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(read(result));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static AgreementStatDelta read(java.sql.ResultSet result) throws SQLException {
        return new AgreementStatDelta(result.getString(1), result.getString(2), result.getString(3),
                result.getString(4), result.getInt(5), result.getInt(6), result.getDouble(7), result.getDouble(8),
                result.getInt(9), result.getInt(10), result.getInt(11), result.getInt(12), result.getLong(13));
    }
}
