package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Scenario bank of M13.6: with {@code debug.recordScenarios} on, every answered decision is written as one JSON file
 * with the exact System One body, which only holds aliases (rule I6), the questions with their options, the answer with
 * its model and input tokens, and an empty label per question for the hand labelling. The folder is
 * {@code plugins/ThePredict/scenarios/}; the composition root chooses it (rule D10).
 *
 * <p>The client hands a decision over only after it completed the result, so recording never delays an answer
 * (rule T2). Serialising and writing run on one daemon thread of this recorder with a queue of {@link #QUEUE_CAPACITY}
 * decisions; a full queue drops the decision and counts it. Each file is written to a temporary file and moved into
 * place atomically, so a reader never sees half a scenario. At most {@link #MAX_SCENARIOS} files per start, named
 * {@code scenario-<UTC start>-<random nonce>-<index>.json} so two starts in the same second never collide (rule A5).
 * Recording never throws to the client: a failed write, a dropped decision and the limit are each counted and reported
 * once, without the body (rule C8). {@link #close()} stops the thread with a bounded wait. Thread-safe.
 */
public final class ScenarioRecorder implements AutoCloseable {
    /** Most scenario files written in one start. */
    public static final int MAX_SCENARIOS = 1000;
    /** Decisions waiting to be written; one more is dropped. */
    public static final int QUEUE_CAPACITY = 64;
    /** Longest wait for pending writes when the recorder closes. */
    static final long CLOSE_WAIT_MILLIS = 1000;
    /** The handle of a decision that was not queued. */
    private static final Runnable NOTHING = () -> {
    };

    private static final DateTimeFormatter RUN = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final Path directory;
    private final boolean enabled;
    private final int limit;
    private final String run;
    private final Consumer<String> log;
    private final Writer writer;
    private final ThreadPoolExecutor thread;
    private final long closeWaitMillis;
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger written = new AtomicInteger();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicBoolean limitReported = new AtomicBoolean();
    private final AtomicBoolean dropReported = new AtomicBoolean();
    private final AtomicBoolean failureReported = new AtomicBoolean();

    /** Writes one document to its final path; the tests replace it with a slow or failing disk. */
    @FunctionalInterface
    interface Writer {
        void write(Path target, String document) throws IOException;
    }

    private ScenarioRecorder(Path directory, boolean enabled, int limit, String run, Consumer<String> log,
                             Writer writer, long closeWaitMillis) {
        this.directory = directory;
        this.enabled = enabled;
        this.limit = limit;
        this.run = run;
        this.log = Objects.requireNonNull(log, "log");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.closeWaitMillis = closeWaitMillis;
        this.thread = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    var daemon = new Thread(runnable, "ThePredict scenario recorder");
                    daemon.setDaemon(true);
                    return daemon;
                });
    }

    /**
     * Returns a recorder that writes nothing and starts no thread, used while {@code debug.recordScenarios} is off.
     *
     * @return the disabled recorder
     */
    public static ScenarioRecorder disabled() {
        return new ScenarioRecorder(Path.of("scenarios"), false, 0, "", line -> {
        }, ScenarioRecorder::atomicWrite, CLOSE_WAIT_MILLIS);
    }

    /**
     * Returns a recorder that writes into a folder, created on the first write.
     *
     * @param directory scenarios folder, such as {@code plugins/ThePredict/scenarios}
     * @param log receiver of the one line written for the limit, for dropped decisions and for failed writes
     * @return the recorder
     */
    public static ScenarioRecorder into(Path directory, Consumer<String> log) {
        return into(directory, MAX_SCENARIOS, Clock.systemUTC(), log, ScenarioRecorder::atomicWrite, CLOSE_WAIT_MILLIS);
    }

    static ScenarioRecorder into(Path directory, int limit, Clock clock, Consumer<String> log, Writer writer,
                                 long closeWaitMillis) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
        var nonce = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
        return new ScenarioRecorder(Objects.requireNonNull(directory, "directory"), true, limit,
                RUN.format(Objects.requireNonNull(clock, "clock").instant()) + "-" + nonce, log, writer, closeWaitMillis);
    }

    /**
     * Tells whether this recorder writes files.
     *
     * @return {@code true} unless it is the disabled recorder
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the folder of the files.
     *
     * @return the folder, empty for the disabled recorder
     */
    public Optional<Path> directory() {
        return enabled ? Optional.of(directory) : Optional.empty();
    }

    /**
     * Counts the files written.
     *
     * @return scenario files written since the start
     */
    public int written() {
        return written.get();
    }

    /**
     * Counts the answered decisions not written because the limit was reached.
     *
     * @return skipped decisions
     */
    public long skipped() {
        return skipped.get();
    }

    /**
     * Counts the answered decisions dropped because the queue of the recorder was full, or still queued when it closed.
     *
     * @return dropped decisions
     */
    public long dropped() {
        return dropped.get();
    }

    /**
     * Counts the writes that failed.
     *
     * @return failed writes
     */
    public long failures() {
        return failures.get();
    }

    /**
     * Queues one answered decision; returns at once and never throws. The client queues it before completing
     * the caller's future, so a close in that same turn still writes the file. If the answer then loses the future to
     * the deadline, the client runs the returned handle and the decision is not written: the bank never keeps an
     * answer the engine received as a timeout.
     *
     * @param body exact request body that was sent
     * @param request the decision request, for its questions and counts; its mob identities are not written
     * @param answer the parsed answer
     * @return a handle that drops the decision if it is still queued
     */
    Runnable record(String body, DecisionRequest request, DecisionResult.Answered answer) {
        if (!enabled || thread.isShutdown()) {
            return NOTHING;
        }
        if (accepted.incrementAndGet() > limit) {
            skipped.incrementAndGet();
            report(limitReported, "Scenario recording reached its limit of " + limit
                    + " files; later decisions are not recorded");
            return NOTHING;
        }
        var cancelled = new AtomicBoolean();
        try {
            thread.execute(() -> {
                if (!cancelled.get()) {
                    write(body, request, answer);
                }
            });
        } catch (RejectedExecutionException exception) {
            accepted.decrementAndGet();
            dropped.incrementAndGet();
            report(dropReported, "Scenario recording dropped a decision: its queue of " + QUEUE_CAPACITY
                    + " is full");
            return NOTHING;
        }
        return () -> cancelled.set(true);
    }

    /**
     * Stops the recording thread: pending writes get at most {@link #CLOSE_WAIT_MILLIS} ms, then the thread is
     * interrupted and the decisions still queued are dropped and counted in {@link #dropped()}. A write cut short leaves
     * at most a temporary file, never a truncated scenario. Idempotent.
     */
    @Override
    public void close() {
        thread.shutdown();
        try {
            if (!thread.awaitTermination(closeWaitMillis, TimeUnit.MILLISECONDS)) {
                dropped.addAndGet(thread.shutdownNow().size());
            }
        } catch (InterruptedException exception) {
            dropped.addAndGet(thread.shutdownNow().size());
            Thread.currentThread().interrupt();
        }
    }

    private void write(String body, DecisionRequest request, DecisionResult.Answered answer) {
        try {
            var index = sequence.incrementAndGet();
            Files.createDirectories(directory);
            writer.write(directory.resolve("scenario-" + run + "-" + String.format(Locale.ROOT, "%04d", index)
                    + ".json"), ScenarioSerializer.document(body, request, answer));
            written.incrementAndGet();
        } catch (IOException | RuntimeException exception) {
            failures.incrementAndGet();
            report(failureReported, "Scenario recording failed: " + exception.getClass().getSimpleName());
        }
    }

    private void report(AtomicBoolean once, String line) {
        if (once.compareAndSet(false, true)) {
            log.accept(line);
        }
    }

    private static void atomicWrite(Path target, String document) throws IOException {
        var temporary = Files.createTempFile(target.getParent(), "scenario-", ".tmp");
        try {
            Files.writeString(temporary, document, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
