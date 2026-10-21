package akyrion.thepredict.adapter.server.vocabulary;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Rate-limited log of vocabulary load messages (rule C8). Each distinct message is written once per
 * load wave (the first start or one {@code /mm reload}); a pause longer than {@link #WAVE_GAP_NANOS}
 * between messages starts a new wave. At most {@link #MAX_MESSAGES_PER_WAVE} messages are written per
 * wave (rule A5), also under concurrent calls. The first wave starts at construction. Thread-safe;
 * the sink must be thread-safe too.
 */
final class LoadWaveLog {
    /** Maximum distinct messages written per load wave. */
    static final int MAX_MESSAGES_PER_WAVE = 32;

    /** Pause that separates two load waves, in nanoseconds (two seconds). */
    static final long WAVE_GAP_NANOS = 2_000_000_000L;

    private final Consumer<String> sink;
    private final LongSupplier nanoClock;
    private final AtomicLong lastMessageNanos = new AtomicLong();
    private final AtomicReference<Wave> wave = new AtomicReference<>(new Wave());

    /**
     * Creates the log.
     *
     * @param sink thread-safe receiver of the written messages
     * @param nanoClock monotonic clock
     */
    LoadWaveLog(Consumer<String> sink, LongSupplier nanoClock) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.lastMessageNanos.set(nanoClock.getAsLong());
    }

    /**
     * Writes a message unless it was already written in this wave or the wave is full.
     *
     * @param message text to write
     * @return whether the message was written
     */
    boolean write(String message) {
        Objects.requireNonNull(message, "message");
        var now = nanoClock.getAsLong();
        var previous = lastMessageNanos.getAndSet(now);
        var current = wave.get();
        if (now - previous > WAVE_GAP_NANOS) {
            var fresh = new Wave();
            current = wave.compareAndSet(current, fresh) ? fresh : wave.get();
        }
        if (current.admit(message)) {
            sink.accept(message);
            return true;
        }
        return false;
    }

    /** Messages written in one wave, with a slot counter that enforces the cap. */
    private static final class Wave {
        private final Set<String> written = ConcurrentHashMap.newKeySet();
        private final AtomicInteger slots = new AtomicInteger();

        /** Admits a new message if a slot is free; a repeated message never takes a slot. */
        private boolean admit(String message) {
            if (written.contains(message)) {
                return false;
            }
            while (true) {
                var taken = slots.get();
                if (taken >= MAX_MESSAGES_PER_WAVE) {
                    return false;
                }
                if (slots.compareAndSet(taken, taken + 1)) {
                    break;
                }
            }
            if (written.add(message)) {
                return true;
            }
            slots.decrementAndGet();
            return false;
        }
    }
}
