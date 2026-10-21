package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Port for the two clocks ThePredict uses (rule C6): server ticks for game mechanics and a monotonic
 * nanosecond clock for real deadlines. Safe to call from any thread.
 *
 * <p>The composition root implements it with the Bukkit tick counter and {@code System.nanoTime};
 * tests use controllable doubles (rule P2).
 */
public interface ServerClock {
    /**
     * Returns the current server tick.
     *
     * @return tick counter of the server
     */
    long currentTick();

    /**
     * Returns the monotonic time used for deadlines such as {@code IntentView.expiresAtNanos}.
     * Values are only meaningful as differences.
     *
     * @return monotonic time in nanoseconds
     */
    long nanoTime();

    /**
     * Builds a clock from two suppliers, for the composition root and for test doubles.
     *
     * @param ticks supplier of the current server tick
     * @param nanos supplier of the monotonic time in nanoseconds
     * @return a clock that delegates to the suppliers
     * @throws NullPointerException if a supplier is {@code null}
     */
    static ServerClock of(LongSupplier ticks, LongSupplier nanos) {
        Objects.requireNonNull(ticks, "ticks");
        Objects.requireNonNull(nanos, "nanos");
        return new ServerClock() {
            @Override
            public long currentTick() {
                return ticks.getAsLong();
            }

            @Override
            public long nanoTime() {
                return nanos.getAsLong();
            }
        };
    }
}
