package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.DifficultyStatDelta;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Upsert of {@code m22_difficulty_stats} (M22.6). Counts and the log time-to-kill use Chan's formula. The start-rung
 * pair keeps the later epoch, and a tie keeps the incoming pair. {@code first_epoch_ms} is the minimum and
 * {@code last_epoch_ms} the maximum.
 */
public final class DifficultyStatsSql {
    /** The upsert. The column {@code excluded} is qualified so it is not the conflict alias. */
    public static final String UPSERT = """
            INSERT INTO m22_difficulty_stats(profile, context, rung, fights, log_ttk_mean, log_ttk_m2, censored,
              excluded, start_rung_estimate, start_rung_epoch_ms, first_epoch_ms, last_epoch_ms)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(profile, context, rung) DO UPDATE SET
              fights = fights + excluded.fights,
              log_ttk_mean = COALESCE(log_ttk_mean + (excluded.log_ttk_mean - log_ttk_mean)
                * CAST(excluded.fights AS REAL) / NULLIF(fights + excluded.fights, 0), log_ttk_mean),
              log_ttk_m2 = COALESCE(log_ttk_m2 + excluded.log_ttk_m2
                + (excluded.log_ttk_mean - log_ttk_mean) * (excluded.log_ttk_mean - log_ttk_mean)
                * CAST(fights AS REAL) * excluded.fights / NULLIF(fights + excluded.fights, 0),
                log_ttk_m2 + excluded.log_ttk_m2),
              censored = censored + excluded.censored,
              excluded = m22_difficulty_stats.excluded + excluded.excluded,
              start_rung_estimate = CASE WHEN excluded.start_rung_epoch_ms >= start_rung_epoch_ms
                THEN excluded.start_rung_estimate ELSE start_rung_estimate END,
              start_rung_epoch_ms = CASE WHEN excluded.start_rung_epoch_ms >= start_rung_epoch_ms
                THEN excluded.start_rung_epoch_ms ELSE start_rung_epoch_ms END,
              first_epoch_ms = MIN(first_epoch_ms, excluded.first_epoch_ms),
              last_epoch_ms = MAX(last_epoch_ms, excluded.last_epoch_ms)
            """;

    private DifficultyStatsSql() {
    }

    /**
     * Applies one delta.
     *
     * @param connection the open transaction
     * @param delta the delta
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, DifficultyStatDelta delta) throws SQLException {
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
    static void bind(java.sql.PreparedStatement statement, DifficultyStatDelta delta) throws SQLException {
        statement.setString(1, delta.profile());
        statement.setString(2, delta.context());
        statement.setInt(3, delta.rung());
        statement.setLong(4, delta.fights());
        statement.setDouble(5, delta.logTtkMean());
        statement.setDouble(6, delta.logTtkM2());
        statement.setLong(7, delta.censored());
        statement.setLong(8, delta.excluded());
        statement.setDouble(9, delta.startRungEstimate());
        statement.setLong(10, delta.startRungEpochMs());
        statement.setLong(11, delta.firstEpochMs());
        statement.setLong(12, delta.lastEpochMs());
    }

    /**
     * Loads at most 50 000 rows of one profile, or every profile when the name is empty.
     *
     * @param connection the connection
     * @param profile empty means every profile
     * @return the rows
     * @throws SQLException when the read fails
     */
    public static List<DifficultyStatDelta> load(Connection connection, String profile) throws SQLException {
        var sql = profile.isEmpty()
                ? "SELECT profile, context, rung, fights, log_ttk_mean, log_ttk_m2, censored, excluded, "
                + "start_rung_estimate, start_rung_epoch_ms, first_epoch_ms, last_epoch_ms "
                + "FROM m22_difficulty_stats ORDER BY last_epoch_ms DESC LIMIT 50000"
                : "SELECT profile, context, rung, fights, log_ttk_mean, log_ttk_m2, censored, excluded, "
                + "start_rung_estimate, start_rung_epoch_ms, first_epoch_ms, last_epoch_ms "
                + "FROM m22_difficulty_stats WHERE profile = ? ORDER BY last_epoch_ms DESC LIMIT 50000";
        try (var statement = connection.prepareStatement(sql)) {
            if (!profile.isEmpty()) {
                statement.setString(1, profile);
            }
            var rows = new ArrayList<DifficultyStatDelta>();
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new DifficultyStatDelta(result.getString(1), result.getString(2), result.getInt(3),
                            result.getLong(4), result.getDouble(5), result.getDouble(6), result.getLong(7),
                            result.getLong(8), result.getDouble(9), result.getLong(10), result.getLong(11),
                            result.getLong(12)));
                }
            }
            return List.copyOf(rows);
        }
    }
}
