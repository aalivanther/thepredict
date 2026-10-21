package akyrion.thepredict.adapter.server.events;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Moves lifecycle work onto the server thread (rule T6), extracted from {@code ThePredictPlugin.runOnServerThread}
 * (M06.2). Safe to call from any thread: the Bukkit scheduler accepts tasks from any thread.
 */
public final class ServerThread {
    /** Warning logged when a task cannot be scheduled, kept from the M01 script log lines. */
    public static final String SCHEDULE_FAILED = "A MythicMobs lifecycle update could not return to the server thread";

    private final BooleanSupplier primaryThread;
    private final Consumer<Runnable> nextTick;
    private final Consumer<String> warnings;

    /**
     * Creates the utility.
     *
     * @param primaryThread {@code Bukkit::isPrimaryThread}
     * @param nextTick runs a task on the server thread on the next tick ({@code BukkitScheduler.runTask})
     * @param warnings receiver of the scheduling warning
     */
    public ServerThread(BooleanSupplier primaryThread, Consumer<Runnable> nextTick, Consumer<String> warnings) {
        this.primaryThread = Objects.requireNonNull(primaryThread, "primaryThread");
        this.nextTick = Objects.requireNonNull(nextTick, "nextTick");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * Runs an operation now if the event is synchronous and this is the server thread; otherwise on the next tick.
     *
     * @param asynchronous {@code Event.isAsynchronous()} of the event
     * @param operation work that touches {@code engine} state
     */
    public void run(boolean asynchronous, Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        if (runsNow(asynchronous)) {
            operation.run();
            return;
        }
        runNextTick(operation);
    }

    /**
     * Tells whether an event may touch {@code engine} state at once: it is synchronous and this is the server
     * thread. Listeners on hot events use it to skip building a task (rule C12).
     *
     * @param asynchronous {@code Event.isAsynchronous()} of the event
     * @return {@code true} if the work may run now
     */
    public boolean runsNow(boolean asynchronous) {
        return !asynchronous && primaryThread.getAsBoolean();
    }

    /**
     * Runs an operation on the server thread on the next tick, never in the current one.
     *
     * @param operation work that touches {@code engine} state
     */
    public void runNextTick(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        try {
            nextTick.accept(operation);
        } catch (RuntimeException exception) {
            warnings.accept(SCHEDULE_FAILED);
        }
    }
}
