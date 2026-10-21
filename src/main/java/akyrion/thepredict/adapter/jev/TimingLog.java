package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.engine.ServerClock;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Rate-limited diagnostics of the internal parse time and the request size (M08.7); since M16.1 the encoding is the
 * {@code PREPARE} span of the engine metrics, measured where it runs. The root passes a sink only when
 * {@code decision.logDecisions} is on; lines carry durations and sizes, never keys, headers or bodies (rule C8).
 * Thread-safe.
 */
final class TimingLog {
    /** Shortest interval between two lines. */
    static final long INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final ServerClock clock;
    private final Consumer<String> sink;
    private final AtomicLong nextNanos;

    TimingLog(ServerClock clock, Consumer<String> sink) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.nextNanos = new AtomicLong(clock.nanoTime());
    }

    long now() {
        return clock.nanoTime();
    }

    void record(long parseNanos, int requestChars) {
        var now = clock.nanoTime();
        var next = nextNanos.get();
        if (now - next >= 0 && nextNanos.compareAndSet(next, now + INTERVAL_NANOS)) {
            sink.accept("SystemOne timings: parse " + TimeUnit.NANOSECONDS.toMicros(parseNanos) + " us, request "
                    + requestChars + " chars");
        }
    }
}
