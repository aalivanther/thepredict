package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.AggregateBatch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Applies one batch in a single transaction (M21.6). A batch whose id is already in {@code runs.last_applied_batch}
 * rolls back and counts as applied. Each table prepares its upsert once per batch and binds every row to it, instead
 * of compiling the statement again for each row (JS-25: on the development machine, the median of 256 outcome rows
 * went from 4.3 ms to 1.3 ms and of 4 096 rows from 61 ms to 12 ms, inside the 5 ms of M21.2 for 256).
 *
 * <p>The transaction is {@code BEGIN IMMEDIATE … COMMIT}, as M21.2 asks: it takes the write lock as it starts. A
 * deferred one takes it at its first write, and in WAL mode a write that another connection committed after the
 * first read of the batch then fails the batch halfway. The three statements go to SQLite as they are and the
 * driver stays in autocommit, so a begin that fails, for example busy while another connection holds the lock,
 * leaves nothing open and the next batch begins cleanly.
 */
final class BatchApply {
    private BatchApply() {
    }

    /**
     * Applies the batch.
     *
     * @param connection the write connection, in autocommit
     * @param batch the batch
     * @param run the run id
     * @return {@code true} when the batch is applied or was already applied
     * @throws SQLException when the begin or a write fails; nothing of the batch is kept
     */
    static boolean apply(Connection connection, AggregateBatch batch, String run) throws SQLException {
        execute(connection, "BEGIN IMMEDIATE");
        try {
            if (batch.batchId() <= lastApplied(connection, run)) {
                execute(connection, "ROLLBACK");
                return true;
            }
            writeRows(connection, batch);
            try (var statement = connection.prepareStatement(
                    "UPDATE runs SET last_applied_batch = ? WHERE run = ?")) {
                statement.setLong(1, batch.batchId());
                statement.setString(2, run);
                statement.executeUpdate();
            }
            execute(connection, "COMMIT");
            return true;
        } catch (SQLException | RuntimeException exception) {
            rollback(connection, exception);
            throw exception;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long lastApplied(Connection connection, String run) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT last_applied_batch FROM runs WHERE run = ?")) {
            statement.setString(1, run);
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private static void writeRows(Connection connection, AggregateBatch batch) throws SQLException {
        upsertAll(connection, OutcomeStatsSql.UPSERT, batch.outcomes(), OutcomeStatsSql::bind);
        upsertAll(connection, DecisionDailySql.UPSERT, batch.decisions(), DecisionDailySql::bind);
        upsertAll(connection, AnswerCacheDailySql.UPSERT, batch.cache(), AnswerCacheDailySql::bind);
        upsertAll(connection, DifficultyStatsSql.UPSERT, batch.difficulty(), DifficultyStatsSql::bind);
        upsertAll(connection, CalibrationBinSql.UPSERT, batch.calibration(), CalibrationBinSql::bind);
        for (var delta : batch.patterns()) {
            PatternStatsSql.upsert(connection, delta);
            PatternStatsSql.noteLearned(connection, delta.profile(), delta.pattern(), delta.targetKind(),
                    delta.mode(), PatternStatsSql.learnedPayload(delta, "neutral"));
        }
        for (var delta : batch.memory()) {
            PlayerMemorySql.apply(connection, delta);
        }
        upsertAll(connection, ValueStatSql.UPSERT, batch.values(), ValueStatSql::bind);
        upsertAll(connection, ChoiceStatSql.UPSERT, batch.choices(), ChoiceStatSql::bind);
        upsertAll(connection, AgreementStatSql.UPSERT, batch.agreements(), AgreementStatSql::bind);
    }

    private static <T> void upsertAll(Connection connection, String sql, List<T> rows, Binder<T> binder)
            throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        try (var statement = connection.prepareStatement(sql)) {
            for (var row : rows) {
                binder.bind(statement, row);
                statement.executeUpdate();
            }
        }
    }

    /**
     * Rolls the batch back after any failure, so no part of it is kept. A failed rollback, for example when SQLite
     * already ended the transaction, is attached to the first failure instead of hiding it.
     */
    private static void rollback(Connection connection, Exception failure) {
        try {
            execute(connection, "ROLLBACK");
        } catch (SQLException exception) {
            failure.addSuppressed(exception);
        }
    }

    /** Binds one row to a prepared upsert. */
    @FunctionalInterface
    private interface Binder<T> {
        void bind(PreparedStatement statement, T row) throws SQLException;
    }
}
