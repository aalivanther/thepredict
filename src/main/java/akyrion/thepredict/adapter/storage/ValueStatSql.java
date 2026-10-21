package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Upsert of {@code m28_value_stats} (M28.1). Chan's formula matches {@code outcome_stats}. Sums for the counters,
 * max for {@code last_epoch_ms}, min for {@code first_epoch_ms}.
 */
public final class ValueStatSql {
    /** Row cap of A5. The writer prunes above it, see {@code TableUpkeep}. */
    public static final int CAP = 100_000;
    /** The upsert. */
    public static final String UPSERT = """
            INSERT INTO m28_value_stats(profile, tactic, context, source, spec, week, windows, reward_mean, reward_m2,
              effect_sum, credit_sum, avoided_sum, jev_seen, jev_agree, first_epoch_ms, last_epoch_ms, extra)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
            ON CONFLICT(profile, tactic, context, source, spec, week) DO UPDATE SET
              windows = windows + excluded.windows,
              reward_mean = COALESCE(reward_mean + (excluded.reward_mean - reward_mean)
                * CAST(excluded.windows AS REAL) / NULLIF(windows + excluded.windows, 0), reward_mean),
              reward_m2 = COALESCE(reward_m2 + excluded.reward_m2
                + (excluded.reward_mean - reward_mean) * (excluded.reward_mean - reward_mean)
                * CAST(windows AS REAL) * excluded.windows / NULLIF(windows + excluded.windows, 0),
                reward_m2 + excluded.reward_m2),
              effect_sum = effect_sum + excluded.effect_sum,
              credit_sum = credit_sum + excluded.credit_sum,
              avoided_sum = avoided_sum + excluded.avoided_sum,
              jev_seen = jev_seen + excluded.jev_seen,
              jev_agree = jev_agree + excluded.jev_agree,
              first_epoch_ms = MIN(first_epoch_ms, excluded.first_epoch_ms),
              last_epoch_ms = MAX(last_epoch_ms, excluded.last_epoch_ms)
            """;

    private ValueStatSql() {
    }

    /**
     * Applies one delta.
     *
     * @param connection the open transaction
     * @param delta the delta
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, ValueStatDelta delta) throws SQLException {
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
    static void bind(java.sql.PreparedStatement statement, ValueStatDelta delta) throws SQLException {
        var key = delta.key();
        statement.setString(1, key.profile());
        statement.setString(2, key.tactic());
        statement.setString(3, key.context());
        statement.setString(4, key.source());
        statement.setString(5, key.spec());
        statement.setInt(6, key.week());
        statement.setInt(7, delta.windows());
        statement.setDouble(8, delta.rewardMean());
        statement.setDouble(9, delta.rewardM2());
        statement.setDouble(10, delta.effectSum());
        statement.setDouble(11, delta.creditSum());
        statement.setDouble(12, delta.avoidedSum());
        statement.setInt(13, delta.jevSeen());
        statement.setInt(14, delta.jevAgree());
        statement.setLong(15, delta.epochMs());
        statement.setLong(16, delta.epochMs());
    }

    /**
     * One keyset page. An empty cursor is the first page. A short page has an empty next cursor.
     *
     * @param rows the rows
     * @param nextCursor cursor of the following page, or empty
     */
    public record Page(List<ValueStatDelta> rows, String nextCursor) {
    }

    /**
     * Reads one page in canonical key order.
     *
     * @param connection the open connection
     * @param profileFilter profile id, empty for every profile
     * @param sinceWeek first week, inclusive
     * @param pageRows rows to return
     * @param cursor previous cursor, or empty
     * @return the page
     * @throws SQLException when the query fails
     */
    public static Page page(Connection connection, String profileFilter, int sinceWeek, int pageRows, String cursor)
            throws SQLException {
        var rows = read(connection, profileFilter, sinceWeek, pageRows, cursor);
        if (rows.size() < pageRows) {
            return new Page(rows, "");
        }
        return new Page(rows, cursor(rows.get(rows.size() - 1).key()));
    }

    private static List<ValueStatDelta> read(Connection connection, String profileFilter, int sinceWeek, int pageRows,
                                             String cursor) throws SQLException {
        var bound = cursor.isEmpty() ? null : decode(cursor);
        try (var statement = connection.prepareStatement("""
                SELECT profile, tactic, context, source, spec, week, windows, reward_mean, reward_m2,
                  effect_sum, credit_sum, avoided_sum, jev_seen, jev_agree, last_epoch_ms
                FROM m28_value_stats
                WHERE (? = '' OR profile = ?) AND week >= ?
                  AND (? = '' OR (profile, tactic, context, source, spec, week) > (?, ?, ?, ?, ?, ?))
                ORDER BY profile, tactic, context, source, spec, week
                LIMIT ?
                """)) {
            bind(statement, profileFilter, sinceWeek, cursor, bound, pageRows);
            try (var result = statement.executeQuery()) {
                var rows = new ArrayList<ValueStatDelta>();
                while (result.next()) {
                    rows.add(row(result));
                }
                return rows;
            }
        }
    }

    private static void bind(java.sql.PreparedStatement statement, String profileFilter, int sinceWeek, String cursor,
                             ValueStatKey bound, int pageRows) throws SQLException {
        statement.setString(1, profileFilter);
        statement.setString(2, profileFilter);
        statement.setInt(3, sinceWeek);
        statement.setString(4, cursor);
        statement.setString(5, bound == null ? "" : bound.profile());
        statement.setString(6, bound == null ? "" : bound.tactic());
        statement.setString(7, bound == null ? "" : bound.context());
        statement.setString(8, bound == null ? "" : bound.source());
        statement.setString(9, bound == null ? "" : bound.spec());
        statement.setInt(10, bound == null ? 0 : bound.week());
        statement.setInt(11, pageRows);
    }

    private static ValueStatDelta row(java.sql.ResultSet result) throws SQLException {
        var key = new ValueStatKey(result.getString(1), result.getString(2), result.getString(3), result.getString(4),
                result.getString(5), result.getInt(6));
        return new ValueStatDelta(key, result.getInt(7), result.getDouble(8), result.getDouble(9), result.getDouble(10),
                result.getDouble(11), result.getDouble(12), result.getInt(13), result.getInt(14), result.getLong(15));
    }

    private static String cursor(ValueStatKey key) {
        var raw = key.profile() + "\u0000" + key.tactic() + "\u0000" + key.context() + "\u0000" + key.source()
                + "\u0000" + key.spec() + "\u0000" + key.week();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static ValueStatKey decode(String cursor) {
        var text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        var parts = text.split("\u0000", -1);
        return new ValueStatKey(parts[0], parts[1], parts[2], parts[3], parts[4], Integer.parseInt(parts[5]));
    }
}
