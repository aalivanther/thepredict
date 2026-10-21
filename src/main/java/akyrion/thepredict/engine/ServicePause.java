package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * The growing pause of {@link ServiceStatusTracker} (M09.4): {@code min(60 s, 1 s × 2^(streak − 1))} times a uniform
 * factor in [0.8, 1.2], or a fixed time such as {@code retry-after}. The streak grows only when a pause starts, so the
 * failures of other requests in flight extend a running pause without making the next one longer. Server thread
 * only; the time comes as a parameter (rule C6).
 */
final class ServicePause {
    private final DoubleSupplier random;
    private int streak;
    private long untilNanos;

    ServicePause(DoubleSupplier random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Starts or extends a pause of the calculated length; returns the nanoseconds left. */
    long calculated(long nowNanos, boolean running) {
        grow(running);
        var doubling = ServiceStatusTracker.BASE_PAUSE_NANOS << Math.min(streak - 1, 6);
        var factor = 0.8 + 0.4 * Math.min(Math.max(random.getAsDouble(), 0.0), 1.0);
        return extend(nowNanos, (long) (Math.min(ServiceStatusTracker.MAX_PAUSE_NANOS, doubling) * factor), running);
    }

    /** Starts or extends a pause of a fixed length; returns the nanoseconds left. */
    long fixed(long nowNanos, long durationNanos, boolean running) {
        grow(running);
        return extend(nowNanos, durationNanos, running);
    }

    /** Tells whether the pause ended. */
    boolean over(long nowNanos) {
        return nowNanos - untilNanos >= 0;
    }

    /** Returns the nanoseconds left in the pause, never negative. */
    long remainingNanos(long nowNanos) {
        return Math.max(0, untilNanos - nowNanos);
    }

    /** Forgets the streak after an answer, so the next pause starts at one second again. */
    void reset() {
        streak = 0;
    }

    private void grow(boolean running) {
        if (!running) {
            streak++;
        }
    }

    private long extend(long nowNanos, long durationNanos, boolean running) {
        var until = nowNanos + durationNanos;
        untilNanos = running && untilNanos - until > 0 ? untilNanos : until;
        return untilNanos - nowNanos;
    }
}
