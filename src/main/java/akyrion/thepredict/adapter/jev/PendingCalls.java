package akyrion.thepredict.adapter.jev;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The public futures of one {@link SystemOneClient}: each one ends by its deadline, aborts its exchange when it
 * completes, and is completed with a {@code client closed} value on close. Admission goes through the bounded
 * executor; a full queue completes the future at once. Thread-safe.
 */
final class PendingCalls {
    private final Map<CompletableFuture<?>, Runnable> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Executor admission;
    private final long deadlineMillis;

    PendingCalls(Executor admission, Duration deadline) {
        this.admission = admission;
        this.deadlineMillis = deadline.toMillis();
    }

    <T> CompletableFuture<T> open(AbortableExchange exchange, T whenClosed, T whenLate) {
        return open(exchange, whenClosed, whenLate, deadlineMillis);
    }

    /** As {@link #open(AbortableExchange, Object, Object)}, with the deadline of this one call. */
    <T> CompletableFuture<T> open(AbortableExchange exchange, T whenClosed, T whenLate, long callDeadlineMillis) {
        var result = new CompletableFuture<T>();
        pending.put(result, () -> result.complete(whenClosed));
        if (closed.get()) {
            result.complete(whenClosed);
        }
        result.whenComplete((value, failure) -> {
            pending.remove(result);
            exchange.abort();
        });
        result.completeOnTimeout(whenLate, callDeadlineMillis, TimeUnit.MILLISECONDS);
        return result;
    }

    <T> void submit(CompletableFuture<T> result, T whenFull, Supplier<CompletableFuture<?>> work,
                    Function<Throwable, T> onFailure) {
        try {
            CompletableFuture.supplyAsync(work, admission).whenComplete((chain, failure) -> {
                if (failure != null) {
                    result.complete(onFailure.apply(failure));
                }
            });
        } catch (RejectedExecutionException exception) {
            result.complete(whenFull);
        }
    }

    boolean close() {
        if (!closed.compareAndSet(false, true)) {
            return false;
        }
        List.copyOf(pending.values()).forEach(Runnable::run);
        return true;
    }
}
