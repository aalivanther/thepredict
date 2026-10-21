package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.DecisionDailyDelta;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Upsert of {@code decision_daily} (M21.6). Counts add. {@code breakdown} stays null in this release. The model is
 * stored as {@code RunIds.dailyModel} names it, so a key pasted into {@code jev.model} never reaches the table.
 */
public final class DecisionDailySql {
    static final String UPSERT = """
            INSERT INTO decision_daily(day, model, profile, question_detail, requests, answered, failed, input_tokens,
              answered_cache, input_tokens_saved, adopted, discarded, breakdown)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,NULL)
            ON CONFLICT(day, model, profile, question_detail) DO UPDATE SET
              requests = requests + excluded.requests,
              answered = answered + excluded.answered,
              failed = failed + excluded.failed,
              input_tokens = input_tokens + excluded.input_tokens,
              answered_cache = answered_cache + excluded.answered_cache,
              input_tokens_saved = input_tokens_saved + excluded.input_tokens_saved,
              adopted = adopted + excluded.adopted,
              discarded = discarded + excluded.discarded
            """;

    private DecisionDailySql() {
    }

    /**
     * Applies one row.
     *
     * @param connection the open transaction
     * @param delta the row
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, DecisionDailyDelta delta) throws SQLException {
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
    static void bind(java.sql.PreparedStatement statement, DecisionDailyDelta delta) throws SQLException {
        statement.setString(1, delta.day());
        statement.setString(2, RunIds.dailyModel(delta.model()));
        statement.setString(3, delta.profile());
        statement.setString(4, delta.questionDetail());
        statement.setLong(5, delta.requests());
        statement.setLong(6, delta.answered());
        statement.setLong(7, delta.failed());
        statement.setLong(8, delta.inputTokens());
        statement.setLong(9, delta.answeredCache());
        statement.setLong(10, delta.inputTokensSaved());
        statement.setLong(11, delta.adopted());
        statement.setLong(12, delta.discarded());
    }
}
