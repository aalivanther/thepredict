package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bounded, deduplicated queue of mobs whose author asked for a re-evaluation with
 * {@code predictordecide} (M04.3). Thread-safe: {@link #offer} runs on whatever thread MythicMobs
 * executes the mechanic (rule T3); {@link #drain} runs on the server thread.
 *
 * <p>A mob is pending at most once: offering it again before it is drained is coalesced and is not a
 * drop. At most {@link #CAPACITY} mobs are pending (rule A5); an offer beyond that is dropped and
 * counted in {@link #droppedCount()}. A slot is reserved before the mob enters the queue, so the queue
 * never exceeds the capacity even under concurrent offers.
 */
public final class ContextSignals {
    /** Maximum number of pending mobs. */
    public static final int CAPACITY = 1024;

    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Queue<UUID> order = new ConcurrentLinkedQueue<>();
    private final AtomicInteger reserved = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();

    /** Creates an empty queue. */
    public ContextSignals() {
    }

    /**
     * Marks a mob for re-evaluation. Any thread; never blocks.
     *
     * @param entityId UUID of the mob
     * @return {@code true} if the mob became pending; {@code false} if it already was or the queue is full
     * @throws NullPointerException if {@code entityId} is {@code null}
     */
    public boolean offer(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");
        if (!pending.add(entityId)) {
            return false;
        }
        if (!reserveSlot()) {
            pending.remove(entityId);
            dropped.incrementAndGet();
            return false;
        }
        order.add(entityId);
        return true;
    }

    /**
     * Delivers pending mobs in arrival order and removes them. Server thread only. A mob offered again
     * after its delivery becomes pending again.
     *
     * @param receiver consumer of each drained UUID
     * @param max maximum number of mobs to deliver, {@code >= 0}
     * @return number of mobs delivered
     * @throws IllegalArgumentException if {@code max} is negative
     */
    public int drain(Consumer<UUID> receiver, int max) {
        Objects.requireNonNull(receiver, "receiver");
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0, was " + max);
        }
        var delivered = 0;
        while (delivered < max) {
            var entityId = order.poll();
            if (entityId == null) {
                break;
            }
            reserved.decrementAndGet();
            pending.remove(entityId);
            delivered++;
            receiver.accept(entityId);
        }
        return delivered;
    }

    /**
     * Counts offers dropped because the queue was full, since creation. Any thread.
     *
     * @return dropped offers, for the M12 diagnostics
     */
    public long droppedCount() {
        return dropped.get();
    }

    /**
     * Counts the pending mobs, including offers still being enqueued. Any thread.
     *
     * @return pending mobs, at most {@link #CAPACITY}
     */
    public int pendingCount() {
        return reserved.get();
    }

    /** Reserves one slot below the capacity; lock-free. */
    private boolean reserveSlot() {
        while (true) {
            var current = reserved.get();
            if (current >= CAPACITY) {
                return false;
            }
            if (reserved.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
