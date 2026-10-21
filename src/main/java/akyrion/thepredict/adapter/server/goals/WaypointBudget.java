package akyrion.thepredict.adapter.server.goals;

import java.util.function.LongSupplier;

/**
 * Paths one tick may ask for (M20.5, A5), shared by every micro goal of the server. The microsecond cap is checked
 * before a path, so one path may pass it; the overrun is then the reason the next path waits. The counters belong to
 * one server tick: {@link #beginTick} starts them again whenever the tick changes, so a cap is never a cap for life.
 */
public final class WaypointBudget {
    private final int maxPaths;
    private final long maxNanos;
    private final LongSupplier nanoTime;
    private long tick = Long.MIN_VALUE;
    private int paths;
    private long spent;

    /**
     * Creates the budget.
     *
     * @param maxPaths {@code decision.tickLayer.maxWaypointPathsPerTick}
     * @param maxMicros {@code decision.tickLayer.maxWaypointPathMicrosPerTick}
     * @param nanoTime clock read around each path
     */
    public WaypointBudget(int maxPaths, int maxMicros, LongSupplier nanoTime) {
        this.maxPaths = maxPaths;
        this.maxNanos = maxMicros * 1_000L;
        this.nanoTime = nanoTime;
    }

    /**
     * Notes the server tick of the next questions. A new tick starts the counters again; the same tick keeps them.
     *
     * @param tick current server tick
     */
    public void beginTick(long tick) {
        if (tick != this.tick) {
            this.tick = tick;
            paths = 0;
            spent = 0;
        }
    }

    /**
     * Whether another path may start.
     *
     * @return {@code true} while both caps still have room
     */
    public boolean allow() {
        return paths < maxPaths && spent < maxNanos;
    }

    /**
     * Whether the last path passed the microsecond cap.
     *
     * @return {@code true} when the spent time is over the cap
     */
    public boolean overrun() {
        return spent > maxNanos;
    }

    /**
     * Paths started this tick.
     *
     * @return the count
     */
    public int paths() {
        return paths;
    }

    /**
     * Counts one path.
     *
     * @param startedNanos clock before the path
     * @param endedNanos clock after the path
     */
    public void spent(long startedNanos, long endedNanos) {
        paths++;
        var elapsed = endedNanos - startedNanos;
        spent += elapsed < 0 ? 0 : elapsed;
    }

    /**
     * Clock, so a goal does not read another.
     *
     * @return the current monotonic nanos
     */
    public long nanoTime() {
        return nanoTime.getAsLong();
    }
}
