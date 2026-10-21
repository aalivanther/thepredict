package akyrion.thepredict.engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters of the aggregate cache (M21.6). Safe to add from the server thread.
 */
public final class StorageMetrics {
    private final AtomicLong batchFailed = new AtomicLong();
    private final AtomicLong ringFull = new AtomicLong();
    private final AtomicLong replyUnknown = new AtomicLong();
    private final AtomicLong keyDropped = new AtomicLong();
    private final AtomicLong snapshotKeyDropped = new AtomicLong();
    private final AtomicLong replies = new AtomicLong();

    /** Creates empty counters. */
    public StorageMetrics() {
    }

    /** Counts a failed batch. */
    public void batchFailed() {
        batchFailed.incrementAndGet();
    }

    /** Counts a tick that refused to build a batch because the ring was full. */
    public void ringFull() {
        ringFull.incrementAndGet();
    }

    /** Counts a reply whose batch was not in the ring. */
    public void replyUnknown() {
        replyUnknown.incrementAndGet();
    }

    /** Counts a new key dropped because the pending map was full. */
    public void keyDropped() {
        keyDropped.incrementAndGet();
    }

    /** Counts a new snapshot key dropped above 50 000. */
    public void snapshotKeyDropped() {
        snapshotKeyDropped.incrementAndGet();
    }

    /**
     * Counts replies applied on one tick.
     *
     * @param count replies
     */
    public void replies(int count) {
        replies.addAndGet(count);
    }

    /**
     * Returns one counter.
     *
     * @param name {@code AGGREGATE_BATCH_FAILED}, {@code AGGREGATE_RING_FULL}, {@code AGGREGATE_REPLY_UNKNOWN},
     *        {@code AGGREGATE_KEY_DROPPED}, {@code AGGREGATE_SNAPSHOT_KEY_DROPPED} or {@code REPLIES}
     * @return the total
     */
    public long total(String name) {
        return switch (name) {
            case "AGGREGATE_BATCH_FAILED" -> batchFailed.get();
            case "AGGREGATE_RING_FULL" -> ringFull.get();
            case "AGGREGATE_REPLY_UNKNOWN" -> replyUnknown.get();
            case "AGGREGATE_KEY_DROPPED" -> keyDropped.get();
            case "AGGREGATE_SNAPSHOT_KEY_DROPPED" -> snapshotKeyDropped.get();
            case "REPLIES" -> replies.get();
            default -> 0;
        };
    }
}
