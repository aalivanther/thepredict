package akyrion.thepredict.engine;

import java.util.function.Consumer;

/**
 * What {@code admin} may ask of storage (M21.9). The server thread calls it; the work runs on the storage thread.
 */
public interface StorageControl {
    /** Most admin watchers at once. */
    int MAX_WATCHERS = 8;

    /** A control that reports aggregates off and refuses commands. */
    StorageControl NONE = new StorageControl() {
        @Override
        public StorageStatus status() {
            return StorageStatus.ABSENT;
        }

        @Override
        public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
            return false;
        }

        @Override
        public boolean atCapacity() {
            return false;
        }
    };

    /**
     * Returns the status block.
     *
     * @return the status
     */
    StorageStatus status();

    /**
     * Queues a command. The watcher is called once on the server thread.
     *
     * @param command the command
     * @param watcher the watcher, at most {@link #MAX_WATCHERS}
     * @return {@code false} when the watcher cap is full or a command of the same kind is already running
     */
    boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher);

    /**
     * Whether {@link #MAX_WATCHERS} commands are already waiting.
     *
     * @return {@code true} when another watcher would be answered {@code busy}
     */
    default boolean atCapacity() {
        return false;
    }
}
