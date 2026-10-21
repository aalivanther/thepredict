package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.CalibrationBinDelta;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Upsert of {@code m22_calibration_bins} (M22.6). Counts and sums add. The epoch keeps the later value.
 */
public final class CalibrationBinSql {
    /** The upsert. */
    public static final String UPSERT = """
            INSERT INTO m22_calibration_bins(model, question_detail, target, bin, n, confidence_sum, success_sum,
              last_epoch_ms)
            VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT(model, question_detail, target, bin) DO UPDATE SET
              n = n + excluded.n,
              confidence_sum = confidence_sum + excluded.confidence_sum,
              success_sum = success_sum + excluded.success_sum,
              last_epoch_ms = MAX(last_epoch_ms, excluded.last_epoch_ms)
            """;

    private CalibrationBinSql() {
    }

    /**
     * Applies one delta.
     *
     * @param connection the open transaction
     * @param delta the delta
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, CalibrationBinDelta delta) throws SQLException {
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
    static void bind(java.sql.PreparedStatement statement, CalibrationBinDelta delta) throws SQLException {
        statement.setString(1, delta.model());
        statement.setString(2, delta.questionDetail());
        statement.setString(3, delta.target());
        statement.setInt(4, delta.bin());
        statement.setLong(5, delta.n());
        statement.setDouble(6, delta.confidenceSum());
        statement.setDouble(7, delta.successSum());
        statement.setLong(8, delta.lastEpochMs());
    }
}
