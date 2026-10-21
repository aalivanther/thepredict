package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.distill.ChoiceStatDelta;
import akyrion.thepredict.domain.distill.ChoiceStatKey;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Upsert and pages of {@code m29_choice_stats} (M29.1). Sums for the counters, max for {@code last_epoch_ms}.
 * {@code TableUpkeep} keeps its cap and retention.
 */
public final class ChoiceStatSql {
    /** Row cap of A5. The writer prunes above it, see {@code TableUpkeep}. */
    public static final int CAP = 200_000;
    /** The upsert. */
    public static final String UPSERT = """
            INSERT INTO m29_choice_stats(profile, regime, context, tactic, source, week, model, offered, chosen,
              last_epoch_ms)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(profile, regime, context, tactic, source, week, model) DO UPDATE SET
              offered = offered + excluded.offered,
              chosen = chosen + excluded.chosen,
              last_epoch_ms = MAX(last_epoch_ms, excluded.last_epoch_ms)
            """;

    private ChoiceStatSql() {
    }

    /**
     * Applies one delta.
     *
     * @param connection the open transaction
     * @param delta the delta
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, ChoiceStatDelta delta) throws SQLException {
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
    static void bind(java.sql.PreparedStatement statement, ChoiceStatDelta delta) throws SQLException {
        var key = delta.key();
        statement.setString(1, key.profile());
        statement.setString(2, key.regime());
        statement.setString(3, key.context());
        statement.setString(4, key.tactic());
        statement.setString(5, key.source());
        statement.setInt(6, key.week());
        statement.setString(7, key.model());
        statement.setInt(8, delta.offered());
        statement.setInt(9, delta.chosen());
        statement.setLong(10, delta.epochMs());
    }

    /**
     * One page, ordered by week and tactic.
     *
     * @param connection the open connection
     * @param profile profile filter, empty for every profile
     * @param sinceWeek first week
     * @param limit page size
     * @return the rows
     * @throws SQLException when the query fails
     */
    public static List<ChoiceStatDelta> page(Connection connection, String profile, int sinceWeek, int limit)
            throws SQLException {
        var sql = profile.isEmpty()
                ? "SELECT profile, regime, context, tactic, source, week, model, offered, chosen, last_epoch_ms"
                + " FROM m29_choice_stats WHERE week >= ? ORDER BY week, tactic LIMIT ?"
                : "SELECT profile, regime, context, tactic, source, week, model, offered, chosen, last_epoch_ms"
                + " FROM m29_choice_stats WHERE profile = ? AND week >= ? ORDER BY week, tactic LIMIT ?";
        var rows = new ArrayList<ChoiceStatDelta>();
        try (var statement = connection.prepareStatement(sql)) {
            var index = 1;
            if (!profile.isEmpty()) {
                statement.setString(index++, profile);
            }
            statement.setInt(index++, sinceWeek);
            statement.setInt(index, limit);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(read(result));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static ChoiceStatDelta read(java.sql.ResultSet result) throws SQLException {
        var key = new ChoiceStatKey(result.getString(1), result.getString(2), result.getString(3),
                result.getString(4), result.getString(5), result.getInt(6), result.getString(7));
        return new ChoiceStatDelta(key, result.getInt(8), result.getInt(9), result.getLong(10));
    }
}
