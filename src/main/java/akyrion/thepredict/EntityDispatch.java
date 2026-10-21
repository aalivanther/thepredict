package akyrion.thepredict;

import java.util.Objects;

/**
 * Entity-owned work (M20.11). Paper runs it inline because the caller is already the server thread. Folia would
 * hand the same task to the entity scheduler; this release does not (T8).
 */
final class EntityDispatch {
    /**
     * Runs the task on the thread that owns the entity. Today that is the caller.
     *
     * @param task work that reads or writes one entity
     */
    void run(Runnable task) {
        Objects.requireNonNull(task, "task").run();
    }
}
