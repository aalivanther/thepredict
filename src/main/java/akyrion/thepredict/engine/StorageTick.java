package akyrion.thepredict.engine;

/**
 * The storage step of the coordinator (M21.6): drain replies and, every flush interval, build one batch.
 */
public interface StorageTick {
    /** A step that does nothing. */
    StorageTick NONE = (tick, nanos) -> {
    };

    /**
     * Runs the step. Server thread only; must stay cheap on ticks that do not flush.
     *
     * @param tick current tick
     * @param nanos monotonic time of the tick
     */
    void onTick(long tick, long nanos);
}
