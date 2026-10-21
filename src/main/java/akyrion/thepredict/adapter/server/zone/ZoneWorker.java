package akyrion.thepredict.adapter.server.zone;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The single daemon thread {@code ThePredict-Zone} (T1-quater, M24.2). Jobs are offered, never blocking the server
 * thread. The thread reads snapshots and packed columns only. It starts at the first offer and retires after about
 * one second without jobs, so it is alive only while builds are pending; the next offer starts it again.
 */
public final class ZoneWorker implements AutoCloseable {
    /** Thread name. There is one. */
    public static final String NAME = "ThePredict-Zone";

    private static final long POLL_MILLIS = 50L;
    private static final int IDLE_POLLS = 20;

    private final ArrayBlockingQueue<Runnable> jobs;
    private final AtomicBoolean active = new AtomicBoolean();
    private volatile Thread thread;
    private volatile boolean running = true;
    private volatile Runnable afterRun;

    /**
     * Creates the worker. No thread runs until the first job is offered.
     *
     * @param capacity queue capacity, at least 1
     */
    public ZoneWorker(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        jobs = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Offers one job and starts the daemon if it is not running. A full queue refuses it.
     *
     * @param job the job
     * @return whether it was queued
     */
    public boolean offer(Runnable job) {
        if (!jobs.offer(job)) {
            return false;
        }
        if (running && active.compareAndSet(false, true)) {
            var started = new Thread(this::loop, NAME);
            started.setDaemon(true);
            thread = started;
            started.start();
        }
        return true;
    }

    /** Parks once after the current or next job, before the following poll. */
    void parkAfterNext(Runnable gate) {
        afterRun = gate;
    }

    /** The current or last daemon, for tests that wait for it to retire; {@code null} before the first offer. */
    Thread current() {
        return thread;
    }

    /**
     * Stops the thread and waits at most one second.
     */
    @Override
    public void close() {
        running = false;
        var current = thread;
        if (current == null) {
            return;
        }
        current.interrupt();
        try {
            current.join(1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Whether the daemon is running.
     *
     * @return {@code true} while it serves or waits for jobs, or when it did not stop in time after {@link #close()}
     */
    public boolean alive() {
        var current = thread;
        return current != null && current.isAlive();
    }

    /**
     * Serves jobs until it retires or is closed. Whatever ends the loop, the active flag is released, so a job that
     * escapes with an error does not leave a dead thread that no offer can replace (SP-17).
     */
    private void loop() {
        try {
            serve();
        } finally {
            active.set(false);
        }
    }

    private void serve() {
        var idle = 0;
        while (running) {
            try {
                var job = jobs.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (job == null) {
                    idle++;
                    if (idle >= IDLE_POLLS && retire()) {
                        return;
                    }
                    continue;
                }
                idle = 0;
                run(job);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (!running) {
                    return;
                }
            }
        }
    }

    private void run(Runnable job) {
        job.run();
        var parked = afterRun;
        if (parked != null) {
            afterRun = null;
            parked.run();
        }
    }

    private boolean retire() {
        active.set(false);
        return jobs.isEmpty() || !active.compareAndSet(false, true);
    }
}
