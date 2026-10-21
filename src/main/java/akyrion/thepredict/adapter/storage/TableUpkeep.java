package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.learn.ValueStatKey;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The prune of the learned tables that A5 bounds (M27.3, M28.1, M29.1). The writer starts a pass when the store opens
 * and every hour, and runs one {@link #step} per loop turn, so a lot of work never blocks a batch for more than one
 * statement. Retention goes first: {@code m27_player_memory} rows older than {@code memory.retentionDays}, and the
 * weeks of {@code m28_value_stats} and of the two {@code m29_*} tables more than {@code weeksKept} before the current
 * week. Then each cap: the oldest rows above it are deleted, the {@code player_paced} rows of {@code m28_value_stats}
 * first. The cut is found once and the rows at or below it are deleted {@link #CHUNK} at a time, so the current week is
 * never emptied to make room. Each table counts what it removed. Only the storage thread calls this.
 */
final class TableUpkeep {
    /** Rows one statement deletes at most. */
    static final int CHUNK = 512;
    /** Counter of memory rows past {@code memory.retentionDays}. */
    static final String MEMORY_EXPIRED = "M27_ROWS_EXPIRED";
    /** Counter of memory rows above {@code memory.maxRows}. */
    static final String MEMORY_TRUNCATED = "M27_ROWS_TRUNCATED";
    /** Counter of value rows past {@code learning.weeksKept}. */
    static final String VALUE_EXPIRED = "M28_WEEKS_PRUNED";
    /** Counter of value rows above the cap (M28.1). */
    static final String VALUE_TRUNCATED = "M28_VALUE_ROWS_TRUNCATED";
    /** Counter of distill rows past {@code distill.weeksKept}. */
    static final String DISTILL_EXPIRED = "M29_WEEKS_PRUNED";
    /** Counter of distill rows above a cap (M29.1). */
    static final String DISTILL_TRUNCATED = "M29_ROWS_TRUNCATED";
    private static final long DAY_MS = 86_400_000L;

    private final StorageLimits limits;
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private final Map<String, AtomicLong> removed = new ConcurrentHashMap<>();
    private volatile boolean running;

    TableUpkeep(StorageLimits limits) {
        this.limits = limits;
    }

    /**
     * Queues a full pass. A pass still running starts over.
     *
     * @param nowMs wall time of the pass, for the retention cut
     */
    void start(long nowMs) {
        steps.clear();
        var week = ValueStatKey.week(nowMs);
        steps.add(new Retention(Table.MEMORY, nowMs - limits.memoryRetentionDays() * DAY_MS));
        steps.add(new Retention(Table.VALUE, (long) week - limits.learning().weeksKept()));
        steps.add(new Retention(Table.CHOICE, (long) week - limits.distillWeeksKept()));
        steps.add(new Retention(Table.AGREEMENT, (long) week - limits.distillWeeksKept()));
        steps.add(new Cap(Table.MEMORY, limits.memoryRows()));
        steps.add(new Cap(Table.VALUE, limits.valueRows()));
        steps.add(new Cap(Table.CHOICE, limits.choiceRows()));
        steps.add(new Cap(Table.AGREEMENT, limits.agreementRows()));
        running = true;
    }

    /**
     * Whether a pass is still running. Any thread may ask.
     *
     * @return {@code true} while steps remain
     */
    boolean active() {
        return running;
    }

    /** Drops the pass in progress, after a failure or when the store stops being writable. */
    void stop() {
        steps.clear();
        running = false;
    }

    /**
     * Runs one statement of the pass.
     *
     * @param connection the write connection, in autocommit
     * @return {@code true} when a statement ran
     * @throws SQLException when the statement fails; the pass is dropped
     */
    boolean step(Connection connection) throws SQLException {
        var head = steps.peekFirst();
        if (head == null) {
            running = false;
            return false;
        }
        try {
            if (head.run(connection)) {
                steps.removeFirst();
                running = !steps.isEmpty();
            }
        } catch (SQLException exception) {
            stop();
            throw exception;
        }
        return true;
    }

    /**
     * Rows removed under one counter since the writer started.
     *
     * @param counter one of the counter names of this class
     * @return the count
     */
    long removed(String counter) {
        var count = removed.get(counter);
        return count == null ? 0 : count.get();
    }

    private void count(String counter, int rows) {
        if (rows > 0) {
            removed.computeIfAbsent(counter, ignored -> new AtomicLong()).addAndGet(rows);
        }
    }

    private static int deleteChunk(Connection connection, Table table, String where, long... values)
            throws SQLException {
        var sql = "DELETE FROM " + table.name + " WHERE (" + table.key + ") IN (SELECT " + table.key + " FROM "
                + table.name + " WHERE " + where + " LIMIT ?)";
        try (var statement = connection.prepareStatement(sql)) {
            for (var index = 0; index < values.length; index++) {
                statement.setLong(index + 1, values[index]);
            }
            statement.setInt(values.length + 1, CHUNK);
            return statement.executeUpdate();
        }
    }

    /** One part of a pass. */
    private interface Step {
        /**
         * Runs one statement.
         *
         * @return {@code true} when this part is finished
         */
        boolean run(Connection connection) throws SQLException;
    }

    /** Deletes the rows before the cut, a chunk per turn. */
    private final class Retention implements Step {
        private final Table table;
        private final long cut;

        private Retention(Table table, long cut) {
            this.table = table;
            this.cut = cut;
        }

        @Override
        public boolean run(Connection connection) throws SQLException {
            var deleted = deleteChunk(connection, table, table.age + " < ?", cut);
            count(table.expired, deleted);
            return deleted < CHUNK;
        }
    }

    /** Counts the table once, finds the cut of the excess once, then deletes the rows at or below it. */
    private final class Cap implements Step {
        private final Table table;
        private final int cap;
        private long[] cut;

        private Cap(Table table, int cap) {
            this.table = table;
            this.cap = cap;
        }

        @Override
        public boolean run(Connection connection) throws SQLException {
            if (cut == null) {
                var excess = rows(connection) - cap;
                if (excess <= 0) {
                    return true;
                }
                cut = cut(connection, excess);
                return cut.length == 0;
            }
            var deleted = deleteChunk(connection, table, "(" + table.order + ") <= (" + table.marks + ")", cut);
            count(table.truncated, deleted);
            return deleted < CHUNK;
        }

        private long rows(Connection connection) throws SQLException {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM " + table.name)) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }

        private long[] cut(Connection connection, long excess) throws SQLException {
            try (var statement = connection.prepareStatement("SELECT " + table.order + " FROM " + table.name
                    + " ORDER BY " + table.order + " LIMIT 1 OFFSET ?")) {
                statement.setLong(1, excess - 1);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        return new long[0];
                    }
                    var values = new long[table.width];
                    for (var index = 0; index < values.length; index++) {
                        values[index] = result.getLong(index + 1);
                    }
                    return values;
                }
            }
        }
    }

    /** A bounded table: its key, its age column for retention and its oldest-first order for the cap. */
    private enum Table {
        MEMORY("m27_player_memory", "rowid", "updated_epoch_ms", "updated_epoch_ms", 1, MEMORY_EXPIRED,
                MEMORY_TRUNCATED),
        VALUE("m28_value_stats", "profile, tactic, context, source, spec, week", "week",
                "CASE WHEN lower(source) = 'player_paced' THEN 0 ELSE 1 END, week, last_epoch_ms", 3, VALUE_EXPIRED,
                VALUE_TRUNCATED),
        CHOICE("m29_choice_stats", "profile, regime, context, tactic, source, week, model", "week",
                "week, last_epoch_ms", 2, DISTILL_EXPIRED, DISTILL_TRUNCATED),
        AGREEMENT("m29_agreement_stats", "artifact, profile, regime, context, week", "week", "week, last_epoch_ms", 2,
                DISTILL_EXPIRED, DISTILL_TRUNCATED);

        private final String name;
        private final String key;
        private final String age;
        private final String order;
        private final int width;
        private final String marks;
        private final String expired;
        private final String truncated;

        Table(String name, String key, String age, String order, int width, String expired, String truncated) {
            this.name = name;
            this.key = key;
            this.age = age;
            this.order = order;
            this.width = width;
            this.marks = width == 1 ? "?" : width == 2 ? "?, ?" : "?, ?, ?";
            this.expired = expired;
            this.truncated = truncated;
        }
    }
}
