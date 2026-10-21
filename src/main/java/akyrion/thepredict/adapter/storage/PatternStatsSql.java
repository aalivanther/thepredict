package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.PatternStatDelta;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Upsert of {@code m26_pattern_stats} (M26.9). Chan's formula matches outcome stats. Nothing here runs on the tick.
 * At most 50 000 rows are loaded, newest {@code last_epoch_ms} first.
 */
public final class PatternStatsSql {
    /** Rows kept when the table is larger. */
    public static final int CAP = 50_000;

    /** The upsert. */
    public static final String UPSERT = """
            INSERT INTO m26_pattern_stats(profile, pattern, target_kind, mode, encounters, activations,
              exploited_casts, exploited_landed, counter_windows, counter_effect_mean, counter_effect_m2,
              first_epoch_ms, last_epoch_ms, extra)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
            ON CONFLICT(profile, pattern, target_kind, mode) DO UPDATE SET
              encounters = encounters + excluded.encounters,
              activations = activations + excluded.activations,
              exploited_casts = exploited_casts + excluded.exploited_casts,
              exploited_landed = exploited_landed + excluded.exploited_landed,
              counter_windows = counter_windows + excluded.counter_windows,
              counter_effect_mean = COALESCE(counter_effect_mean
                + (excluded.counter_effect_mean - counter_effect_mean)
                * CAST(excluded.counter_windows AS REAL) / NULLIF(counter_windows + excluded.counter_windows, 0),
                counter_effect_mean),
              counter_effect_m2 = COALESCE(counter_effect_m2 + excluded.counter_effect_m2
                + (excluded.counter_effect_mean - counter_effect_mean)
                * (excluded.counter_effect_mean - counter_effect_mean)
                * CAST(counter_windows AS REAL) * excluded.counter_windows
                / NULLIF(counter_windows + excluded.counter_windows, 0),
                counter_effect_m2 + excluded.counter_effect_m2),
              first_epoch_ms = MIN(first_epoch_ms, excluded.first_epoch_ms),
              last_epoch_ms = excluded.last_epoch_ms
            """;

    /** Writes {@code extra.learned} and does not touch {@code counter_effect_mean}. */
    public static final String NOTE_LEARNED = """
            UPDATE m26_pattern_stats SET extra = ?
            WHERE profile = ? AND pattern = ? AND target_kind = ? AND mode = ?
            """;

    private PatternStatsSql() {
    }

    /**
     * JSON stored in {@code extra}. The column {@code counter_effect_mean} is not a parameter of the update.
     *
     * @param delta the row identity and the counts copied into the JSON
     * @param result {@code landed}, {@code avoided}, {@code blocked} or {@code neutral}
     * @return the JSON object
     */
    public static String learnedPayload(PatternStatDelta delta, String result) {
        var landed = "landed".equals(result) ? 1 : 0;
        var avoided = "avoided".equals(result) ? 1 : 0;
        var blocked = "blocked".equals(result) ? 1 : 0;
        var neutral = "neutral".equals(result) ? 1 : 0;
        return "{\"learned\":{\"windows\":" + delta.counterWindows() + ",\"landed\":" + landed + ",\"avoided\":"
                + avoided + ",\"blocked\":" + blocked + ",\"neutral\":" + neutral + ",\"counter_effect_mean\":"
                + delta.counterEffectMean() + ",\"counter_effect_m2\":" + delta.counterEffectM2() + "}}";
    }

    /**
     * Stores {@code extra.learned}. {@code counter_effect_mean} stays at the value the upsert wrote.
     *
     * @param connection the open connection
     * @param profile profile key
     * @param pattern pattern name
     * @param targetKind {@code player} or {@code entity}
     * @param mode pattern mode
     * @param learnedJson the payload from {@link #learnedPayload}
     * @throws SQLException when the update fails
     */
    public static void noteLearned(Connection connection, String profile, String pattern, String targetKind,
                                   String mode, String learnedJson) throws SQLException {
        try (var statement = connection.prepareStatement(NOTE_LEARNED)) {
            statement.setBytes(1, learnedJson.getBytes(StandardCharsets.UTF_8));
            statement.setString(2, profile);
            statement.setString(3, pattern);
            statement.setString(4, targetKind);
            statement.setString(5, mode);
            statement.executeUpdate();
        }
    }

    /**
     * Applies one delta.
     *
     * @param connection the open connection
     * @param delta the delta
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, PatternStatDelta delta) throws SQLException {
        try (var statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, delta.profile());
            statement.setString(2, delta.pattern());
            statement.setString(3, delta.targetKind());
            statement.setString(4, delta.mode());
            statement.setLong(5, delta.encounters());
            statement.setLong(6, delta.activations());
            statement.setLong(7, delta.exploitedCasts());
            statement.setLong(8, delta.exploitedLanded());
            statement.setLong(9, delta.counterWindows());
            statement.setDouble(10, delta.counterEffectMean());
            statement.setDouble(11, delta.counterEffectM2());
            statement.setLong(12, delta.firstEpochMs());
            statement.setLong(13, delta.lastEpochMs());
            statement.executeUpdate();
        }
    }

    /**
     * Loads at most {@link #CAP} rows.
     *
     * @param connection the connection
     * @return the rows
     * @throws SQLException when the read fails
     */
    public static List<PatternStatsLoaded> load(Connection connection) throws SQLException {
        var sql = "SELECT profile, pattern, target_kind, mode, encounters, activations, exploited_casts, "
                + "exploited_landed, counter_windows, counter_effect_mean, counter_effect_m2, first_epoch_ms, "
                + "last_epoch_ms FROM m26_pattern_stats ORDER BY last_epoch_ms DESC LIMIT " + (CAP + 1);
        var rows = new ArrayList<PatternStatsLoaded>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            while (result.next() && rows.size() < CAP) {
                rows.add(new PatternStatsLoaded(result.getString(1), result.getString(2), result.getString(3),
                        result.getString(4), result.getLong(5), result.getLong(6), result.getLong(7),
                        result.getLong(8), result.getLong(9), result.getDouble(10), result.getDouble(11),
                        result.getLong(12), result.getLong(13)));
            }
        }
        return rows;
    }
}
