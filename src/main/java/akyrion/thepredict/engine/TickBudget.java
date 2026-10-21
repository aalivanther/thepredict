package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Time cap of one pass of the tick layer (M20.3). The clock is read after each cast, after every eight visits without
 * one and after each release. One instance serves every tick: {@link #reset} starts a pass without allocating
 * (rule C12). Server thread only.
 */
final class TickBudget {
    private long origin;
    private long maxNanos;
    private LongSupplier nanos;
    private int quiet;
    private long elapsed;

    /**
     * A budget for one pass.
     *
     * @param origin clock at the start of the pass
     * @param maxMicros time cap of the pass
     * @param nanos clock, read only on the budget points
     */
    TickBudget(long origin, int maxMicros, LongSupplier nanos) {
        reset(origin, maxMicros, nanos);
    }

    /**
     * Starts a new pass on this instance.
     *
     * @param origin clock at the start of the pass
     * @param maxMicros time cap of the pass
     * @param nanos clock, read only on the budget points
     * @return this budget
     */
    TickBudget reset(long origin, int maxMicros, LongSupplier nanos) {
        this.origin = origin;
        this.maxNanos = maxMicros * 1000L;
        this.nanos = Objects.requireNonNull(nanos, "nanos");
        quiet = 0;
        elapsed = 0L;
        return this;
    }

    /**
     * Notes one visit of the ring.
     *
     * @param cast whether the visit cast
     * @return whether the cap is spent
     */
    boolean note(boolean cast) {
        if (!cast && ++quiet < 8) {
            return false;
        }
        quiet = 0;
        return read();
    }

    /**
     * Notes one release. A release always reads the clock. The visit budget is not spent.
     *
     * @return whether the cap is spent
     */
    boolean noteRelease() {
        return read();
    }

    /**
     * Whether the time read last stayed under half of the cap.
     *
     * @return {@code true} below half
     */
    boolean underHalf() {
        return elapsed * 2 < maxNanos;
    }

    private boolean read() {
        elapsed = nanos.getAsLong() - origin;
        return elapsed > maxNanos;
    }
}
