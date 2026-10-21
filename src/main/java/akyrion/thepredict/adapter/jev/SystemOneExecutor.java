package akyrion.thepredict.adapter.jev;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bounded executor of one {@link SystemOneClient} (M08.1): {@value #THREADS} fixed daemon threads named
 * {@code ThePredict-SystemOne-N} and a queue of {@value #QUEUE_CAPACITY} tasks. A full queue rejects new work
 * instead of growing. Used for admission, encoding and parsing; never handed to the JDK HTTP client, whose
 * selector would close for good after a rejected task.
 */
final class SystemOneExecutor {
    /** Number of worker threads. */
    static final int THREADS = 2;
    /** Largest number of queued tasks. */
    static final int QUEUE_CAPACITY = 64;
    /** Thread name prefix. */
    static final String THREAD_PREFIX = "ThePredict-SystemOne-";
    /** Longest wait for the threads to end on close. */
    static final Duration CLOSE_WAIT = Duration.ofMillis(500);

    private final ThreadPoolExecutor executor;

    SystemOneExecutor() {
        var counter = new AtomicInteger();
        executor = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    var thread = new Thread(runnable, THREAD_PREFIX + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    ExecutorService executor() {
        return executor;
    }

    /**
     * Returns the executor for parsing a response that already arrived. If the queue is full the parse runs on
     * the thread that completed the JDK future (the JVM-wide common {@code ForkJoinPool}), so a paid answer is never discarded
     * for lack of queue space; admission of new work is what the bounded queue limits.
     */
    Executor parsing() {
        return task -> {
            try {
                executor.execute(task);
            } catch (RejectedExecutionException exception) {
                if (executor.isShutdown()) {
                    throw exception;
                }
                task.run();
            }
        };
    }

    void shutdownNow() {
        executor.shutdownNow();
    }

    void awaitTermination() {
        try {
            executor.awaitTermination(CLOSE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    boolean terminated() {
        return executor.isTerminated();
    }
}
