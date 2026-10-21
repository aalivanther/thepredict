package akyrion.thepredict.engine;

/**
 * Port of the aggregate database (M21.6). {@link #submit} and {@link #request} run on the server thread and never
 * block; the work happens on {@code ThePredict-Storage}.
 */
public interface AggregateStore {
    /**
     * A store that accepts every batch and drops it without counting a failure. Its health is
     * {@link StorageState#DISABLED}, so the cache does not leave batches waiting.
     */
    AggregateStore NONE = new AggregateStore() {
        @Override
        public StorageHealth health() {
            return StorageHealth.DISABLED;
        }

        @Override
        public boolean submit(AggregateBatch batch) {
            return true;
        }

        @Override
        public boolean request(AggregateQuery query) {
            return true;
        }
    };

    /**
     * Returns the last published health.
     *
     * @return the health; O(1)
     */
    StorageHealth health();

    /**
     * Queues a batch.
     *
     * @param batch the batch
     * @return {@code false} when the queue is full; the cache keeps the batch
     */
    boolean submit(AggregateBatch batch);

    /**
     * Queues a read.
     *
     * @param query the read
     * @return {@code false} when the queue is full
     */
    boolean request(AggregateQuery query);
}
