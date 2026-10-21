package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bounded hand-over of results from HTTP threads to the server thread (M09.2, rules A4, A5, T5). {@link #offer}
 * runs on any thread and never blocks; {@link #drain} and {@link #clear} run on the server thread only. The memory
 * never grows beyond the fixed capacity: a full queue rejects the entry and counts it, and the coordinator's
 * watchdog releases the request whose result was lost.
 *
 * @param <T> immutable entry type
 */
public final class Inbox<T> {
    /** Storage replies waiting for the server thread (M21.6). Separate from the HTTP lane. */
    public static final int STORAGE_CAPACITY = 64;

    private final ArrayBlockingQueue<T> queue;
    private final int capacity;
    private final ArrayBlockingQueue<StorageReply> storage = new ArrayBlockingQueue<>(STORAGE_CAPACITY);
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong storageRejected = new AtomicLong();

    /**
     * Creates an empty inbox.
     *
     * @param capacity fixed number of entries, {@code >= 1}
     * @throws IllegalArgumentException if the capacity is not positive
     */
    public Inbox(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        queue = new ArrayBlockingQueue<>(capacity);
        this.capacity = capacity;
    }

    /**
     * Deposits an entry. Any thread; never blocks and never throws for a full queue.
     *
     * @param entry immutable entry
     * @return {@code false} if the queue was full; the rejection is counted
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    public boolean offer(T entry) {
        if (queue.offer(Objects.requireNonNull(entry, "entry"))) {
            return true;
        }
        rejected.incrementAndGet();
        return false;
    }

    /**
     * Delivers entries in arrival order and removes them; each entry is delivered once. Server thread only.
     *
     * @param maximum most entries to deliver, {@code >= 0}
     * @param receiver consumer of each entry
     * @return number of entries delivered
     * @throws IllegalArgumentException if {@code maximum} is negative
     */
    public int drain(int maximum, Consumer<T> receiver) {
        Objects.requireNonNull(receiver, "receiver");
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must be >= 0, was " + maximum);
        }
        var delivered = 0;
        while (delivered < maximum) {
            var entry = queue.poll();
            if (entry == null) {
                break;
            }
            delivered++;
            receiver.accept(entry);
        }
        return delivered;
    }

    /**
     * Drops every waiting result of the HTTP lane; part of the shutdown (rule T7). Server thread only. The storage
     * lane is left to its own drain, which runs until the storage writer has stopped (M21.6), so a confirmation the
     * writer sends while it closes is not lost (EFO-26).
     */
    public void clear() {
        queue.clear();
    }

    /**
     * Counts the waiting entries. Any thread.
     *
     * @return entries not yet drained, at most the capacity
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns the fixed capacity. It is the configured value, never a sum of two reads that a concurrent offer could
     * split (EFO-26).
     *
     * @return maximum waiting entries
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Counts the entries rejected because the queue was full, since creation. Any thread.
     *
     * @return rejected entries
     */
    public long rejected() {
        return rejected.get();
    }

    /**
     * Deposits a storage reply. Any thread; never blocks.
     *
     * @param reply the reply
     * @return {@code false} when the storage lane is full
     */
    public boolean offerStorage(StorageReply reply) {
        if (storage.offer(Objects.requireNonNull(reply, "reply"))) {
            return true;
        }
        storageRejected.incrementAndGet();
        return false;
    }

    /**
     * Delivers storage replies in arrival order. Server thread only.
     *
     * @param maximum most replies to deliver
     * @param receiver consumer of each reply
     * @return number delivered
     */
    public int drainStorage(int maximum, Consumer<StorageReply> receiver) {
        Objects.requireNonNull(receiver, "receiver");
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must be >= 0, was " + maximum);
        }
        var delivered = 0;
        while (delivered < maximum) {
            var reply = storage.poll();
            if (reply == null) {
                break;
            }
            delivered++;
            receiver.accept(reply);
        }
        return delivered;
    }

    /**
     * Storage replies rejected because the lane was full.
     *
     * @return the count
     */
    public long storageRejected() {
        return storageRejected.get();
    }
}
