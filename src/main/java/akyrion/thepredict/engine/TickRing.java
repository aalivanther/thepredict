package akyrion.thepredict.engine;

import java.util.List;

/**
 * Rotary cursor over the sessions enrolled in opening order (M20.3). A session left out this tick is among the
 * first of the next. Comparisons are not visits: the caller decides which indexes count.
 */
public final class TickRing {
    private int cursor;

    /** Starts at index 0. */
    public TickRing() {
    }

    /**
     * Where the next pass starts.
     *
     * @return the cursor
     */
    public int cursor() {
        return cursor;
    }

    /**
     * Index of the next session, wrapping in enrollment order.
     *
     * @param size number of enrolled sessions
     * @return the index, or 0 when the ring is empty
     */
    public int next(int size) {
        if (size < 1) {
            return 0;
        }
        var index = Math.floorMod(cursor, size);
        cursor = (index + 1) % size;
        return index;
    }

    /**
     * Remembers where a pass stopped.
     *
     * @param index index of the next session to consider
     */
    public void resume(int index) {
        cursor = index;
    }
}
