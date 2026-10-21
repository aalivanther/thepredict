package akyrion.thepredict.engine;

import akyrion.thepredict.domain.HintKind;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The declared prefetch entry (M27.4, T6). {@code offer} does not block and does not touch Bukkit, the cache or the
 * engine. The server thread drains at most {@code maxHintsPerTick} hints every tick (errata E27-7 applied). The ring
 * is fixed so a tick does not lock a blocking queue or copy the drained hints twice.
 */
public final class MemoryPrefetchHints {
    /** Capacity of the queue. */
    public static final int CAPACITY = 256;

    private final PrefetchHint[] slots = new PrefetchHint[CAPACITY];
    private int head;
    private int tail;
    private int size;
    private long dropped;

    /**
     * Starts with an empty ring.
     */
    public MemoryPrefetchHints() {
    }

    /**
     * Offers one hint. A full ring drops it.
     *
     * @param subject the player
     * @param kind why the hint was offered
     * @param allowed whether pre-login allowed the player; ignored for join and quit
     * @return {@code true} when the hint was queued
     */
    public synchronized boolean offer(UUID subject, HintKind kind, boolean allowed) {
        if (kind == HintKind.PRE_LOGIN && !allowed) {
            return false;
        }
        if (size == CAPACITY) {
            dropped++;
            return false;
        }
        slots[tail] = new PrefetchHint(subject, kind);
        tail++;
        if (tail == CAPACITY) {
            tail = 0;
        }
        size++;
        return true;
    }

    /**
     * Drains at most {@code limit} hints. Hints past the cap stay in the ring.
     *
     * @param limit the cap
     * @return the hints, in arrival order
     */
    public synchronized List<PrefetchHint> drain(int limit) {
        var count = Math.min(Math.max(limit, 0), size);
        if (count == 0) {
            return List.of();
        }
        var taken = new PrefetchHint[count];
        for (var index = 0; index < count; index++) {
            taken[index] = slots[head];
            slots[head] = null;
            head++;
            if (head == CAPACITY) {
                head = 0;
            }
        }
        size -= count;
        return Arrays.asList(taken);
    }

    /**
     * Hints dropped because the ring was full.
     *
     * @return the count
     */
    public synchronized long dropped() {
        return dropped;
    }
}
