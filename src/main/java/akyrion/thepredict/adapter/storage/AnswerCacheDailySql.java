package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.AnswerCacheDelta;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Upsert of {@code answer_cache_daily} (M21.6). Counts add. The percentile is replaced when the delta has one, and
 * kept when it has none. The model is stored as {@code RunIds.dailyModel} names it.
 */
public final class AnswerCacheDailySql {
    static final String UPSERT = """
            INSERT INTO answer_cache_daily(day, model, mode, lookups, hits, compared, agree_best, confidence_delta_p95)
            VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT(day, model, mode) DO UPDATE SET
              lookups = lookups + excluded.lookups,
              hits = hits + excluded.hits,
              compared = compared + excluded.compared,
              agree_best = agree_best + excluded.agree_best,
              confidence_delta_p95 = COALESCE(excluded.confidence_delta_p95, confidence_delta_p95)
            """;

    private AnswerCacheDailySql() {
    }

    /**
     * Applies one row.
     *
     * @param connection the open transaction
     * @param delta the row
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, AnswerCacheDelta delta) throws SQLException {
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
    static void bind(java.sql.PreparedStatement statement, AnswerCacheDelta delta) throws SQLException {
        statement.setString(1, delta.day());
        statement.setString(2, RunIds.dailyModel(delta.model()));
        statement.setString(3, delta.mode());
        statement.setLong(4, delta.lookups());
        statement.setLong(5, delta.hits());
        statement.setLong(6, delta.compared());
        statement.setLong(7, delta.agreeBest());
        if (Double.isNaN(delta.confidenceDeltaP95())) {
            statement.setNull(8, java.sql.Types.REAL);
        } else {
            statement.setDouble(8, delta.confidenceDeltaP95());
        }
    }
}
