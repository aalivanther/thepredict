package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.engine.OutcomeStatDelta;
import akyrion.thepredict.engine.StorageReply;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Upsert of {@code outcome_stats} with Chan's formula in REAL arithmetic (M21.5). SQLite reads the old row for every
 * assignment, so the order of the {@code SET} does not matter. A delta of no window on a row of no window keeps the
 * stored mean and adds the spreads, instead of dividing by zero, which would write {@code NULL} into a
 * {@code NOT NULL} column and lose the whole batch.
 */
public final class OutcomeStatsSql {
    /** The upsert. Integer division is avoided with {@code CAST}; a zero denominator keeps the stored mean. */
    public static final String UPSERT = """
            INSERT INTO outcome_stats(profile, tactic, movement, level, context, windows, effect_mean, effect_m2,
              dealt_sum, taken_sum, reached, mob_died, target_died, casts, hits, first_epoch_ms, last_epoch_ms, extra)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
            ON CONFLICT(profile, tactic, movement, level, context) DO UPDATE SET
              windows = windows + excluded.windows,
              effect_mean = COALESCE(effect_mean + (excluded.effect_mean - effect_mean)
                * CAST(excluded.windows AS REAL) / NULLIF(windows + excluded.windows, 0), effect_mean),
              effect_m2 = COALESCE(effect_m2 + excluded.effect_m2
                + (excluded.effect_mean - effect_mean) * (excluded.effect_mean - effect_mean)
                * CAST(windows AS REAL) * excluded.windows / NULLIF(windows + excluded.windows, 0),
                effect_m2 + excluded.effect_m2),
              dealt_sum = dealt_sum + excluded.dealt_sum,
              taken_sum = taken_sum + excluded.taken_sum,
              reached = reached + excluded.reached,
              mob_died = mob_died + excluded.mob_died,
              target_died = target_died + excluded.target_died,
              casts = casts + excluded.casts,
              hits = hits + excluded.hits,
              last_epoch_ms = excluded.last_epoch_ms
            """;

    private OutcomeStatsSql() {
    }

    /**
     * Applies one delta.
     *
     * @param connection the open transaction
     * @param delta the delta
     * @throws SQLException when the upsert fails
     */
    public static void upsert(Connection connection, OutcomeStatDelta delta) throws SQLException {
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
    static void bind(java.sql.PreparedStatement statement, OutcomeStatDelta delta) throws SQLException {
        var key = delta.key();
        var stat = delta.stat();
        statement.setString(1, key.profile());
        statement.setString(2, key.tactic().value());
        statement.setString(3, key.movement().name());
        statement.setString(4, key.level().name());
        statement.setString(5, key.context());
        statement.setLong(6, stat.windows());
        statement.setDouble(7, stat.effectMean());
        statement.setDouble(8, stat.effectM2());
        statement.setDouble(9, stat.dealtSum());
        statement.setDouble(10, stat.takenSum());
        statement.setLong(11, stat.reached());
        statement.setLong(12, stat.mobDied());
        statement.setLong(13, stat.targetDied());
        statement.setLong(14, stat.casts());
        statement.setLong(15, stat.hits());
        statement.setLong(16, delta.epochMs());
        statement.setLong(17, delta.epochMs());
    }

    /**
     * Loads at most 50 000 rows, newest {@code last_epoch_ms} first.
     *
     * @param connection the connection
     * @param profiles empty means every profile
     * @return the rows and whether the cap truncated them
     * @throws SQLException when the read fails
     */
    public static Loaded load(Connection connection, List<String> profiles) throws SQLException {
        return load(connection, profiles, 50_000);
    }

    /**
     * Loads at most {@code cap} rows, newest {@code last_epoch_ms} first. One extra row marks the load truncated.
     *
     * @param connection the connection
     * @param profiles empty means every profile
     * @param cap most rows kept
     * @return the rows and whether one more row existed
     * @throws SQLException when the read fails
     */
    static Loaded load(Connection connection, List<String> profiles, int cap) throws SQLException {
        var sql = "SELECT profile, tactic, movement, level, context, windows, effect_mean, effect_m2, dealt_sum, "
                + "taken_sum, reached, mob_died, target_died, casts, hits FROM outcome_stats";
        if (!profiles.isEmpty()) {
            sql += " WHERE profile IN (" + "?,".repeat(profiles.size()).replaceAll(",$", "") + ")";
        }
        sql += " ORDER BY last_epoch_ms DESC LIMIT " + (cap + 1);
        var rows = new ArrayList<StorageReply.LoadedRow>();
        try (var statement = connection.prepareStatement(sql)) {
            for (var index = 0; index < profiles.size(); index++) {
                statement.setString(index + 1, profiles.get(index));
            }
            try (var result = statement.executeQuery()) {
                var truncated = false;
                while (result.next()) {
                    if (rows.size() == cap) {
                        truncated = true;
                        break;
                    }
                    var key = new OutcomeStatKey(result.getString(1),
                            new akyrion.thepredict.domain.TacticId(result.getString(2)),
                            akyrion.thepredict.domain.Movement.valueOf(result.getString(3)),
                            akyrion.thepredict.domain.ControlLevel.valueOf(result.getString(4)),
                            result.getString(5));
                    var stat = new OutcomeStat(result.getLong(6), result.getDouble(7), result.getDouble(8),
                            result.getDouble(9), result.getDouble(10), result.getLong(11), result.getLong(12),
                            result.getLong(13), result.getLong(14), result.getLong(15));
                    rows.add(new StorageReply.LoadedRow(key, stat));
                }
                return new Loaded(rows, truncated);
            }
        }
    }

    /**
     * A load.
     *
     * @param rows the rows
     * @param truncated whether the cap dropped a row
     */
    public record Loaded(List<StorageReply.LoadedRow> rows, boolean truncated) {
    }
}
