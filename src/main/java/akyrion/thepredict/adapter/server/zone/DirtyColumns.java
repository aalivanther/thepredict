package akyrion.thepredict.adapter.server.zone;

import java.util.function.BiPredicate;

/**
 * Bounded ring of dirty column indexes and the tick of the event (M24.3). A repeated index keeps the later tick.
 * A full ring sets the overflow flag and drops the newest key. The cap is an A5 limit.
 */
public final class DirtyColumns {
    private final int[] indexes;
    private final long[] ticks;
    private int size;
    private boolean overflow;

    /**
     * Creates the queue.
     *
     * @param capacity maximum entries
     */
    public DirtyColumns(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        indexes = new int[capacity];
        ticks = new long[capacity];
    }

    /**
     * Offers one column. A duplicate updates the tick.
     *
     * @param index column index
     * @param tick event tick
     * @return {@code false} when the ring was full and the column was dropped
     */
    public boolean offer(int index, long tick) {
        for (var cursor = 0; cursor < size; cursor++) {
            if (indexes[cursor] == index) {
                ticks[cursor] = tick;
                return true;
            }
        }
        if (size == indexes.length) {
            overflow = true;
            return false;
        }
        indexes[size] = index;
        ticks[size] = tick;
        size++;
        return true;
    }

    /**
     * How many columns are waiting.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * The capacity.
     *
     * @return the cap
     */
    public int capacity() {
        return indexes.length;
    }

    /**
     * Whether a full ring dropped a column.
     *
     * @return {@code true} after a dropped offer
     */
    public boolean overflowed() {
        return overflow;
    }

    /**
     * Column index at a position.
     *
     * @param cursor position, {@code 0} to {@link #size()} exclusive
     * @return the index
     */
    public int indexAt(int cursor) {
        return indexes[cursor];
    }

    /**
     * Event tick at a position.
     *
     * @param cursor position
     * @return the tick
     */
    public long tickAt(int cursor) {
        return ticks[cursor];
    }

    /**
     * Drops entries the predicate refuses. The arguments are the column index and the event tick. A ring with room
     * again is no longer overflowed (SP-16).
     *
     * @param keep which entries stay
     */
    public void retain(BiPredicate<Integer, Long> keep) {
        var to = 0;
        for (var cursor = 0; cursor < size; cursor++) {
            if (!keep.test(indexes[cursor], ticks[cursor])) {
                continue;
            }
            indexes[to] = indexes[cursor];
            ticks[to] = ticks[cursor];
            to++;
        }
        size = to;
        if (size < indexes.length) {
            overflow = false;
        }
    }

    /** Drops every entry and clears the overflow flag. */
    public void clear() {
        size = 0;
        overflow = false;
    }
}
