package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AnswerSource;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Steps 1 and 2 of the coordinator (M09.1): drains the {@link Inbox}, releases the requests whose deadline passed and
 * starts the service checks. Server thread only.
 *
 * <p>A finished request that is still the one in flight of its encounter frees its budget, is measured and counted;
 * every result, on time or late, informs the {@link ServiceStatusTracker} and is handed to
 * {@link ExecutionSteps#accept} (M10 discards a late one by identity). A request whose deadline passed is released as
 * {@code TIMEOUT} and its future cancelled, so a lost answer never blocks an encounter; the cancellation the client
 * then deposits is the echo of that release and is dropped (EFO-20).
 *
 * <p>For {@code /thepredict check} (M12.2) up to {@link #MAX_CHECK_WATCHERS} watchers wait for the next check that
 * leaves the service out of {@code CHECKING}; each is called once, on the server thread, with the check as the client
 * reported it. The model of the last answer is kept for {@code /thepredict status}, labelled so that an unrecognized
 * value is never shown.
 *
 * <p>Every result handed to the acceptance, including the synthetic {@code TIMEOUT} of an expired request and a late
 * one, is first told to the {@link DecisionTap} with its round trip (M18.4).
 */
public final class ResultIntake {
    /** Most operators waiting at once for the result of a check. */
    public static final int MAX_CHECK_WATCHERS = 8;

    private final Inbox<InboxEntry> inbox;
    private final InFlightRegistry inFlight;
    private final RequestBudget budget;
    private final ServiceStatusTracker tracker;
    private final DecisionClient client;
    private final DecisionMetrics metrics;
    private final ExecutionSteps steps;
    private final ServerClock clock;
    private final DecisionTap tap;
    private final AnswerCache cache;
    private final IntConsumer storageDrain;
    private final int storageReplies;
    private long reportedRejections;
    private final List<Consumer<ServiceCheck>> checkWatchers = new ArrayList<>();
    private Optional<String> lastModel = Optional.empty();

    /**
     * Installs the teaching board. A Jev answer reads it and writes no choice row.
     *
     * @param board the board shared with the capture
     * @param sink where a non-Jev answer would send rows
     */
    public void useTeaching(akyrion.thepredict.engine.distill.TeachingBoard board,
                            java.util.function.Consumer<java.util.List<akyrion.thepredict.domain.distill.ChoiceStatDelta>> sink) {
        teaching = java.util.Objects.requireNonNull(board, "board");
        choices = java.util.Objects.requireNonNull(sink, "sink");
    }

    /**
     * Installs the teaching board with a sink that keeps the rows only for the caller to replace.
     *
     * @param board the board
     */
    public void useTeaching(akyrion.thepredict.engine.distill.TeachingBoard board) {
        useTeaching(board, rows -> {
        });
    }
    private akyrion.thepredict.engine.distill.TeachingBoard teaching =
            akyrion.thepredict.engine.distill.TeachingBoard.NONE;
    private java.util.function.Consumer<java.util.List<akyrion.thepredict.domain.distill.ChoiceStatDelta>> choices =
            rows -> {
            };

    /**
     * Creates the intake.
     *
     * @param inbox inbox filled by the HTTP threads
     * @param inFlight requests in flight
     * @param budget request and token budget
     * @param tracker state of the service
     * @param client decision client, for the checks
     * @param metrics latency and counters
     * @param steps acceptance of M10
     * @param clock server clock
     * @param tap listener told of each result right before its acceptance (M18.4); {@link DecisionTap#NONE} for none
     */
    public ResultIntake(Inbox<InboxEntry> inbox, InFlightRegistry inFlight, RequestBudget budget,
                        ServiceStatusTracker tracker, DecisionClient client, DecisionMetrics metrics,
                        ExecutionSteps steps, ServerClock clock, DecisionTap tap) {
        this(inbox, inFlight, budget, tracker, client, metrics, steps, clock, tap, AnswerCache.off(metrics),
                ignored -> {
                }, 4);
    }

    /**
     * Creates the intake with an answer cache and a storage-reply drain (M21).
     *
     * @param inbox inbox filled by the HTTP threads
     * @param inFlight requests in flight
     * @param budget request and token budget
     * @param tracker state of the service
     * @param client decision client, for the checks
     * @param metrics latency and counters
     * @param steps acceptance of M10
     * @param clock server clock
     * @param tap listener told of each result
     * @param cache answer cache; off counts nothing
     * @param storageDrain called with the reply cap at the start of {@link #drain}
     * @param storageReplies replies the drain asks for
     */
    public ResultIntake(Inbox<InboxEntry> inbox, InFlightRegistry inFlight, RequestBudget budget,
                        ServiceStatusTracker tracker, DecisionClient client, DecisionMetrics metrics,
                        ExecutionSteps steps, ServerClock clock, DecisionTap tap, AnswerCache cache,
                        IntConsumer storageDrain, int storageReplies) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.storageDrain = Objects.requireNonNull(storageDrain, "storageDrain");
        this.storageReplies = storageReplies;
        this.inFlight = Objects.requireNonNull(inFlight, "inFlight");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.client = Objects.requireNonNull(client, "client");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.steps = Objects.requireNonNull(steps, "steps");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tap = Objects.requireNonNull(tap, "tap");
    }

    /**
     * Step 1: handles at most {@code maximum} entries of the inbox. A failure of M10 on one entry never skips the
     * others; the first one is rethrown at the end for the coordinator to count and log.
     *
     * @param maximum {@code decision.maxCompletionsPerTick}
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     * @return entries handled
     */
    public int drain(int maximum, long tick, long nowNanos) {
        storageDrain.accept(storageReplies);
        var failures = new Failures();
        var drained = inbox.drain(maximum, entry -> {
            switch (entry) {
                case InboxEntry.CompletedCheck check -> checked(check, nowNanos, failures);
                case InboxEntry.CompletedRequest completed -> completed(completed, tick, nowNanos, failures);
                case StorageReply ignored -> {
                }
            }
        });
        var rejected = inbox.rejected();
        metrics.add(DecisionMetrics.Counter.INBOX_REJECTIONS, rejected - reportedRejections);
        reportedRejections = rejected;
        failures.rethrow();
        return drained;
    }

    /**
     * Step 2: releases every request whose deadline passed as {@code TIMEOUT}, then starts a service check if one is
     * due. The budget of every expired request is freed before any of them reaches M10, and a failure of M10 on one
     * never skips the others; the first such failure is rethrown at the end for the coordinator to count and log.
     *
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     * @return requests released
     */
    public int expire(long tick, long nowNanos) {
        var expired = inFlight.expire(nowNanos);
        var timeout = new DecisionResult.Failed(FailureKind.TIMEOUT, Optional.empty(), "no answer before the deadline");
        for (var request : expired) {
            budget.complete(request, OptionalInt.empty(), nowNanos);
            metrics.add(DecisionMetrics.Counter.WATCHDOG_RELEASES, 1);
            metrics.increment(FailureKind.TIMEOUT);
            tracker.onResult(timeout, nowNanos);
        }
        startCheck(nowNanos);
        var failures = new Failures();
        expired.forEach(request -> {
            failures.run(() -> tap.resultReceived(request.encounter(), request.sequence(), timeout,
                    nowNanos - request.sentAtNanos()));
            failures.run(() -> steps.accept(new InboxEntry.CompletedRequest(request, timeout, nowNanos), tick,
                    nowNanos));
        });
        failures.rethrow();
        return expired.size();
    }

    /**
     * Cancels every request in flight and drops the waiting results; part of the shutdown (rule T7).
     *
     * @return cancelled requests
     */
    public int cancelAll() {
        var cancelled = inFlight.cancelAll();
        inbox.clear();
        return cancelled.size();
    }

    /**
     * Waits for the result of the next check that leaves {@code CHECKING}, for {@code /thepredict check}.
     *
     * @param watcher called once on the server thread with the check as the client reported it
     * @return {@code false} when {@link #MAX_CHECK_WATCHERS} watchers already wait; the watcher is not kept
     */
    public boolean awaitCheck(Consumer<ServiceCheck> watcher) {
        Objects.requireNonNull(watcher, "watcher");
        if (checkWatchers.size() >= MAX_CHECK_WATCHERS) {
            return false;
        }
        checkWatchers.add(watcher);
        return true;
    }

    /**
     * Returns the model that answered last.
     *
     * @return the effective model of the last answer, or {@code <unrecognized model>}; empty before any answer
     */
    public Optional<String> lastModel() {
        return lastModel;
    }

    private void checked(InboxEntry.CompletedCheck check, long nowNanos, Failures failures) {
        tracker.onCheck(check.generation(), check.check(), nowNanos);
        if (tracker.state() == ServiceState.CHECKING || checkWatchers.isEmpty()) {
            return;
        }
        var watchers = List.copyOf(checkWatchers);
        checkWatchers.clear();
        watchers.forEach(watcher -> failures.run(() -> watcher.accept(check.check())));
    }

    /**
     * Accepts a result whose future was already done when {@code decide} returned (M20.7). The decisions step runs
     * after the tick layer, so the layer casts the new intent on the next tick. Does not also enqueue the inbox.
     *
     * @param completed finished request
     * @param tick current server tick
     * @param nowNanos monotonic time
     */
    public void acceptNow(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
        var failures = new Failures();
        completed(completed, tick, nowNanos, failures);
        failures.rethrow();
    }

    /**
     * Applies a cache hit. Skips the budget, the tracker, {@code ROUNDTRIP} and the token sample.
     *
     * @param request the request built for this tick, not registered in flight
     * @param answered the cached answer, with {@link AnswerSource#CACHE} and zero tokens
     * @param tick current server tick
     * @param nowNanos monotonic time
     */
    public void acceptCached(InFlightRequest request, DecisionResult.Answered answered, long tick, long nowNanos) {
        var completed = new InboxEntry.CompletedRequest(request, answered, nowNanos);
        var failures = new Failures();
        failures.run(() -> tap.resultReceived(request.encounter(), request.sequence(), answered, 0));
        failures.run(() -> steps.accept(completed, tick, nowNanos));
        failures.rethrow();
    }

    private void completed(InboxEntry.CompletedRequest completed, long tick, long nowNanos, Failures failures) {
        var request = completed.request();
        var result = completed.result();
        if (inFlight.remove(request)) {
            budget.complete(request, tokens(result), nowNanos);
            metrics.record(DecisionMetrics.Span.ROUNDTRIP, completed.completedAtNanos() - request.sentAtNanos(), nowNanos);
            count(result);
        } else if (cancelledHere(result)) {
            return;
        }
        if (result instanceof DecisionResult.Answered answered && teaching.keeps()) {
            choices.accept(akyrion.thepredict.engine.distill.ChoiceIntake.answer(answered.source(), teaching, 0L));
        }
        var drainedAt = clock.nanoTime();
        metrics.record(DecisionMetrics.Span.INBOX_WAIT, drainedAt - completed.completedAtNanos(), drainedAt);
        tracker.onResult(result, nowNanos);
        if (result instanceof DecisionResult.Answered answered && answered.source() == AnswerSource.LIVE
                && !request.sentBody().isEmpty()) {
            cache.remember(cache.modelName(), request.sentBody(), request.questions().keySet(), answered,
                    request.captureTick());
            AnswerCache.first(answered).ifPresent(best -> cache.compare(request.encounter(), request.sequence(),
                    best.getKey(), best.getValue()));
        }
        failures.run(() -> tap.resultReceived(request.encounter(), request.sequence(), result,
                completed.completedAtNanos() - request.sentAtNanos()));
        failures.run(() -> steps.accept(completed, tick, nowNanos));
        var applied = clock.nanoTime();
        metrics.record(DecisionMetrics.Span.APPLY, applied - drainedAt, applied);
    }

    /**
     * The echo of a request this side already released (EFO-20): {@code expire} and {@code cancelAll} cancel the future
     * and the dispatcher's completion then deposits a {@code NETWORK} failure whose detail is
     * {@link DecisionAcceptor#CANCELLED}. The release already told the tap its {@code TIMEOUT}; this entry is not a
     * second result, so it reaches neither the tap nor the acceptance, where it would count as a stale discard.
     */
    private static boolean cancelledHere(DecisionResult result) {
        return result instanceof DecisionResult.Failed failed && DecisionAcceptor.CANCELLED.equals(failed.detail());
    }

    private void startCheck(long nowNanos) {
        var generation = tracker.checkToStart(nowNanos);
        if (generation.isEmpty()) {
            return;
        }
        var checkGeneration = generation.getAsLong();
        CompletableFuture<ServiceCheck> future;
        try {
            future = client.check();
        } catch (RuntimeException exception) {
            tracker.onCheck(checkGeneration, new ServiceCheck(ServiceState.PAUSED, "the check could not start"), nowNanos);
            return;
        }
        future.whenComplete((check, failure) -> inbox.offer(new InboxEntry.CompletedCheck(checkGeneration,
                check != null ? check : new ServiceCheck(ServiceState.PAUSED, "the check future failed"),
                clock.nanoTime())));
    }

    private void count(DecisionResult result) {
        switch (result) {
            case DecisionResult.Answered answered -> {
                lastModel = Optional.of(ModelExpectation.label(answered.effectiveModel()));
                metrics.add(DecisionMetrics.Counter.ANSWERED, 1);
                metrics.add(DecisionMetrics.Counter.INPUT_TOKENS, answered.inputTokens());
            }
            case DecisionResult.Failed failed -> metrics.increment(failed.kind());
        }
    }

    /** Runs each hand-over to M10 in its own boundary and keeps the first failure to rethrow once all have run. */
    private static final class Failures {
        private RuntimeException first;

        private void run(Runnable work) {
            try {
                work.run();
            } catch (RuntimeException exception) {
                if (first == null) {
                    first = exception;
                }
            }
        }

        private void rethrow() {
            if (first != null) {
                throw first;
            }
        }
    }

    private static OptionalInt tokens(DecisionResult result) {
        return result instanceof DecisionResult.Answered answered ? OptionalInt.of(answered.inputTokens())
                : OptionalInt.empty();
    }
}
