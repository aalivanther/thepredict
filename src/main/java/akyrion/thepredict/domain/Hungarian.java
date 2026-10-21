package akyrion.thepredict.domain;

/**
 * Minimum assignment (M25). Kuhn–Munkres on caller-sized buffers owned by this instance.
 * Ties keep the lower column because the scan uses a strict less-than. A chosen cost of
 * {@link GroupFairnessRules#INFEASIBLE} or more is a programming error.
 */
public final class Hungarian {
    private final int[] u;
    private final int[] v;
    private final int[] parent;
    private final int[] way;
    private final int[] minv;
    private final boolean[] used;
    private final int[][] cost;

    /**
     * Allocates buffers for up to {@code max} rows and columns.
     *
     * @param max largest side, at least 1
     */
    public Hungarian(int max) {
        if (max < 1 || max > 24) {
            throw new IllegalArgumentException("max must be within [1, 24], was " + max);
        }
        var n = max + 1;
        u = new int[n];
        v = new int[n];
        parent = new int[n];
        way = new int[n];
        minv = new int[n];
        used = new boolean[n];
        cost = new int[n][n];
    }

    /**
     * Assigns each row to one column.
     *
     * @param rows row-major costs, {@code n} rows and {@code m} columns, {@code m >= n}
     * @param rowCount rows
     * @param columnCount columns
     * @param assignment filled with the chosen column of each row
     * @return the total cost
     * @throws IllegalStateException when a chosen cost is at least {@link GroupFairnessRules#INFEASIBLE}
     */
    public int assign(int[][] rows, int rowCount, int columnCount, int[] assignment) {
        if (rowCount < 0 || columnCount < rowCount || columnCount >= cost.length) {
            throw new IllegalArgumentException("assignment shape");
        }
        clear(columnCount);
        for (var row = 1; row <= rowCount; row++) {
            for (var column = 1; column <= columnCount; column++) {
                cost[row][column] = rows[row - 1][column - 1];
            }
        }
        for (var row = 1; row <= rowCount; row++) {
            augment(row, columnCount);
        }
        return read(rowCount, columnCount, assignment);
    }

    private void augment(int row, int columnCount) {
        parent[0] = row;
        var column = 0;
        java.util.Arrays.fill(minv, 0, columnCount + 1, GroupFairnessRules.SENTINEL);
        java.util.Arrays.fill(used, 0, columnCount + 1, false);
        do {
            used[column] = true;
            var next = nearest(parent[column], column, columnCount);
            if (next == 0) {
                throw new IllegalStateException("assignment cost");
            }
            shift(minv[next], columnCount);
            column = next;
        } while (parent[column] != 0);
        while (column != 0) {
            var previous = way[column];
            parent[column] = parent[previous];
            column = previous;
        }
    }

    /**
     * Lowers the reduced cost of each free column from row {@code current} and returns the free column with the
     * smallest one, the lower column on a tie, or {@code 0} when none is left. That smallest value stays in
     * {@code minv[next]}, so it is the step the potentials move by.
     */
    private int nearest(int current, int column, int columnCount) {
        var delta = GroupFairnessRules.SENTINEL;
        var next = 0;
        for (var candidate = 1; candidate <= columnCount; candidate++) {
            if (used[candidate]) {
                continue;
            }
            var reduced = cost[current][candidate] - u[current] - v[candidate];
            if (reduced < minv[candidate]) {
                minv[candidate] = reduced;
                way[candidate] = column;
            }
            if (minv[candidate] < delta) {
                delta = minv[candidate];
                next = candidate;
            }
        }
        return next;
    }

    private void shift(int delta, int columnCount) {
        for (var index = 0; index <= columnCount; index++) {
            if (used[index]) {
                u[parent[index]] += delta;
                v[index] -= delta;
            } else {
                minv[index] -= delta;
            }
        }
    }

    private int read(int rowCount, int columnCount, int[] assignment) {
        var total = 0;
        for (var row = 0; row < rowCount; row++) {
            assignment[row] = -1;
        }
        for (var column = 1; column <= columnCount; column++) {
            var row = parent[column];
            if (row == 0) {
                continue;
            }
            var chosen = cost[row][column];
            if (chosen >= GroupFairnessRules.INFEASIBLE) {
                throw new IllegalStateException("assignment cost");
            }
            assignment[row - 1] = column - 1;
            total += chosen;
        }
        return total;
    }

    private void clear(int columnCount) {
        java.util.Arrays.fill(u, 0);
        java.util.Arrays.fill(v, 0, columnCount + 1, 0);
        java.util.Arrays.fill(parent, 0, columnCount + 1, 0);
        java.util.Arrays.fill(way, 0, columnCount + 1, 0);
    }
}
