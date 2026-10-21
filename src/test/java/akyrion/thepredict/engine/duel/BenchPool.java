package akyrion.thepredict.engine.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * Runs independent jobs of the effectiveness bench on a fixed number of threads and returns their results in job
 * order. Every job builds its own simulator, engine and world, and {@code src/main} keeps no mutable static state, so
 * the results do not depend on the number of threads ({@code BenchPoolTest} compares one thread with four). The pool
 * is closed by its owner.
 */
final class BenchPool implements AutoCloseable {
    private final ExecutorService executor;
    private final int threads;

    BenchPool(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1, was " + threads);
        }
        this.threads = threads;
        this.executor = threads == 1 ? null : Executors.newFixedThreadPool(threads, runnable -> {
            var thread = new Thread(runnable, "bench");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** How many threads the pool runs. */
    int threads() {
        return threads;
    }

    /**
     * Runs {@code count} jobs in chunks and returns their results in job order.
     *
     * @param count how many jobs
     * @param job the job of an index
     * @param <T> the result
     * @return the results, the result of job {@code i} at index {@code i}
     */
    <T> List<T> map(int count, IntFunction<T> job) {
        var results = new ArrayList<T>(count);
        if (executor == null) {
            for (var index = 0; index < count; index++) {
                results.add(job.apply(index));
            }
            return results;
        }
        var chunk = Math.max(1, count / (threads * 8));
        var futures = new ArrayList<Future<List<T>>>();
        for (var from = 0; from < count; from += chunk) {
            var start = from;
            var end = Math.min(count, from + chunk);
            futures.add(executor.submit(() -> {
                var part = new ArrayList<T>(end - start);
                for (var index = start; index < end; index++) {
                    part.add(job.apply(index));
                }
                return part;
            }));
        }
        for (var future : futures) {
            results.addAll(join(future));
        }
        return results;
    }

    private static <T> List<T> join(Future<List<T>> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(exception.getCause());
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
