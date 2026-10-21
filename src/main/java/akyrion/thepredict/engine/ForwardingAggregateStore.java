package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * An {@link AggregateStore} whose target starts as {@link AggregateStore#NONE} and can be replaced once the writer
 * exists (M21.6). The server thread calls {@link #use}; {@link #submit} and {@link #request} never block.
 */
public final class ForwardingAggregateStore implements AggregateStore {
    private volatile AggregateStore target = NONE;

    /** Creates a store that drops batches until {@link #use}. */
    public ForwardingAggregateStore() {
    }

    /**
     * Replaces the target.
     *
     * @param store the writer, or {@link AggregateStore#NONE}
     */
    public void use(AggregateStore store) {
        target = Objects.requireNonNull(store, "store");
    }

    @Override
    public StorageHealth health() {
        return target.health();
    }

    @Override
    public boolean submit(AggregateBatch batch) {
        return target.submit(batch);
    }

    @Override
    public boolean request(AggregateQuery query) {
        return target.request(query);
    }
}
