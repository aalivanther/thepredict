package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.distill.CellOrders;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.TacticOrder;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code m29_choice_stats} for {@code /thepredict distill build} one page per writer turn (M29.6, errata E29-8
 * applied). It reads and does not train: this release writes no {@code .tpld} on the server, {@code distillTrain}
 * builds it, and the note says so. Pages follow the primary key, so a batch applied between two turns never makes a
 * row skipped or read twice, and a page costs the same at the end of the table as at its start. The order of each
 * cell is computed only when it is asked for.
 */
public final class DistillTableChunk {
    /** One slice of the writer. */
    public static final long SLICE_NANOS = 5_000_000L;
    private static final String COLUMNS = "profile, regime, context, tactic, source, week, model";

    private DistillTableChunk() {
    }

    /**
     * Rows read and the table they produced.
     *
     * @param rows rows read
     * @param chunks chunks used
     * @param cells tactic orders
     */
    public record Built(int rows, int chunks, CellOrders cells) {
    }

    /**
     * One table read. Each {@link Job#turn} reads a single page, so a writer turn stays one slice. A full page
     * resumes on the next turn.
     */
    public static final class Job {
        private String profile = "";
        private Row last;
        private int rows;
        private int chunks;
        private boolean begun;
        private boolean done = true;
        private final LinkedHashMap<String, LinkedHashMap<String, Integer>> totals = new LinkedHashMap<>();

        /**
         * Starts idle, with no page read.
         */
        public Job() {
        }

        /**
         * Starts a read of every profile.
         */
        public void begin() {
            begin("");
        }

        /**
         * Starts a read of one profile, or of every profile with an empty filter.
         *
         * @param profileFilter profile id, empty for every profile
         */
        public void begin(String profileFilter) {
            profile = profileFilter;
            last = null;
            rows = 0;
            chunks = 0;
            begun = true;
            done = false;
            totals.clear();
        }

        /**
         * Whether {@link #begin} has run.
         *
         * @return the flag
         */
        public boolean begun() {
            return begun;
        }

        /**
         * Whether another page is waiting.
         *
         * @return {@code true} when the table is complete
         */
        public boolean done() {
            return done;
        }

        /**
         * The table assembled so far, ordered now.
         *
         * @return the orders
         */
        public CellOrders cells() {
            return orders(totals);
        }

        /**
         * The writer note.
         *
         * @return rows and chunks, and that no artifact was written
         */
        public String note() {
            return "read " + rows + " rows in " + chunks + " chunks; no artifact written, distillTrain builds the .tpld";
        }

        /**
         * Drops a failed read to an empty table.
         */
        public void fail() {
            done = true;
            rows = 0;
            chunks = 0;
            totals.clear();
        }

        /**
         * Reads one page. A page that fills the limit leaves the rest for the next turn.
         *
         * @param connection the writer connection, or {@code null} when the store is not open
         * @param chunkRows rows per slice, capped at 2000
         * @throws SQLException when the read fails
         */
        public void turn(Connection connection, int chunkRows) throws SQLException {
            if (done) {
                return;
            }
            if (connection == null) {
                done = true;
                return;
            }
            var limit = Math.min(Math.max(chunkRows, 1), 2000);
            var read = page(connection, profile, last, limit);
            chunks++;
            rows += read.size();
            merge(totals, read);
            if (!read.isEmpty()) {
                last = read.getLast();
            }
            done = read.size() < limit;
        }

        /**
         * The snapshot of this job.
         *
         * @return rows, chunks and cells
         */
        public Built built() {
            return new Built(rows, chunks, cells());
        }
    }

    private static void merge(Map<String, LinkedHashMap<String, Integer>> totals, List<Row> page) {
        for (var row : page) {
            totals.computeIfAbsent(row.context(), key -> new LinkedHashMap<>()).merge(row.tactic(), row.chosen(),
                    Integer::sum);
        }
    }

    private static List<Row> page(Connection connection, String profile, Row after, int limit) throws SQLException {
        var rows = new ArrayList<Row>();
        try (var statement = connection.prepareStatement("SELECT " + COLUMNS + ", chosen FROM m29_choice_stats"
                + " WHERE (? = '' OR profile = ?) AND (? = 0 OR (" + COLUMNS + ") > (?, ?, ?, ?, ?, ?, ?))"
                + " ORDER BY " + COLUMNS + " LIMIT ?")) {
            statement.setString(1, profile);
            statement.setString(2, profile);
            statement.setInt(3, after == null ? 0 : 1);
            var from = after == null ? new Row("", "", "", "", "", 0, "", 0) : after;
            statement.setString(4, from.profile());
            statement.setString(5, from.regime());
            statement.setString(6, from.context());
            statement.setString(7, from.tactic());
            statement.setString(8, from.source());
            statement.setInt(9, from.week());
            statement.setString(10, from.model());
            statement.setInt(11, limit);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new Row(result.getString(1), result.getString(2), result.getString(3),
                            result.getString(4), result.getString(5), result.getInt(6), result.getString(7),
                            result.getInt(8)));
                }
            }
        }
        return rows;
    }

    private static CellOrders orders(Map<String, LinkedHashMap<String, Integer>> totals) {
        var map = new LinkedHashMap<String, TacticOrder>();
        for (var entry : totals.entrySet()) {
            if (map.size() == CellOrders.MAX_CELLS) {
                break;
            }
            var ranked = new ArrayList<>(entry.getValue().entrySet());
            ranked.sort((left, right) -> Integer.compare(right.getValue(), left.getValue()));
            var tactics = new ArrayList<String>();
            var strength = new ArrayList<Integer>();
            for (var tactic : ranked) {
                if (tactics.size() == TacticOrder.MAX) {
                    break;
                }
                tactics.add(tactic.getKey());
                strength.add(Math.max(0, tactic.getValue()));
            }
            map.put(entry.getKey(), new TacticOrder(tactics, strength, DistillSource.TABLE));
        }
        return new CellOrders(map);
    }

    private record Row(String profile, String regime, String context, String tactic, String source, int week,
                       String model, int chosen) {
    }
}
