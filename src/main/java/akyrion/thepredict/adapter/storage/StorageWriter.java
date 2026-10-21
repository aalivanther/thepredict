package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.AggregateQuery;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageHealth;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import akyrion.thepredict.engine.distill.DistillControl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * The daemon {@code ThePredict-Storage} (M21.2). Offers never block. Reads run before batches, and batches before
 * events. When the reply buffer is full, reads and batches wait (errata E21-1) and events still append; while closing,
 * nobody reads the replies, so the queued batches are applied anyway. A turn that moves nothing waits up to 100 ms for
 * a read instead of spinning, and an event that cannot be written while records are paused for size or a full disk is
 * dropped and counted as {@code paused} (M21.3). Each step of a turn catches at its frontier, so one failure never
 * kills the thread; an interrupt asks the thread to finish. The thread also runs, one slice per turn, the
 * {@code distill build} and {@code learn build} jobs and the hourly prune of the learned tables (A5, T1-ter).
 */
public final class StorageWriter {
    /** Thread name. */
    public static final String THREAD_NAME = "ThePredict-Storage";
    /** Event lines written per pass. */
    public static final int EVENT_BATCH = 4096;

    private static final int STEPS = 5;
    private static final long IDLE_NANOS = 100_000_000L;
    private static final long CLOSING_NANOS = 10_000_000L;
    private static final long FLUSH_NANOS = 1_000_000_000L;
    private static final long MINUTE_NANOS = 60_000_000_000L;
    private static final long HOUR_NANOS = 3_600_000_000_000L;
    private static final long FULL_DISK_BYTES = 64L * 1024L * 1024L;

    private final Spec spec;
    private final Inbox<InboxEntry> inbox;
    private final WriterTrace trace;
    private volatile DistillControl distillControl = DistillControl.passthrough();
    private volatile ProfileBases profileBases = ProfileBases.UNKNOWN;
    private final DistillTableChunk.Job distillTable = new DistillTableChunk.Job();
    private long distillCommand;
    private final LearnBuildJob learnBuild;
    private final TableUpkeep upkeep;
    private final ArrayBlockingQueue<RecordEvent> events;
    private final ArrayBlockingQueue<RecordEvent> bodyEvents;
    private final ArrayBlockingQueue<AggregateBatch> batches = new ArrayBlockingQueue<>(64);
    private final ArrayBlockingQueue<Work> reads = new ArrayBlockingQueue<>(256);
    private final ArrayDeque<StorageReply> replies = new ArrayDeque<>();
    private final ConcurrentHashMap<String, AtomicLong> dropped = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicInteger passes = new AtomicInteger();
    private final AtomicLong droppedTotal = new AtomicLong();
    private final AtomicLong internalFailures = new AtomicLong();
    private final AtomicReference<Runnable> onStop = new AtomicReference<>();
    private final List<Long> ioTimes = new ArrayList<>();
    private final List<SegmentCompressor.Job> compressing = new ArrayList<>();
    private volatile StorageState aggregateState = StorageState.STARTING;
    private volatile String cause = "";
    private volatile String maintenanceCause = "";
    private volatile String sqliteVersion = "";
    private volatile String synchronous = "";
    private volatile String journalMode = "";
    private volatile boolean diskPaused;
    private volatile boolean capPaused;
    private volatile int diskNotices;
    private volatile int diskRetries;
    private volatile long diskRetryNanos = 300_000_000_000L;
    private volatile LongSupplier usableSpace;
    private long diskPausedAt = Long.MIN_VALUE;
    private volatile boolean eventsFailed;
    private volatile String eventsCause = "";
    private volatile boolean network;
    private volatile boolean abandoned;
    private volatile boolean stopped;
    private volatile AggregateBatch finalBatch;
    private volatile long dropsBeforeClose;
    private volatile String unapplied = "";
    private Work held;
    private long bytesTotal;
    private Connection write;
    private Connection read;
    private SegmentFiles segments;
    private SegmentFiles bodySegments;
    private long flushAt;
    private long nextCheckpoint;
    private long nextHourly;
    private long nextBackup;
    private final Thread thread;

    /**
     * What the writer needs. Built by {@link StorageService}.
     *
     * @param directory records directory
     * @param database database file, beside {@code records} when that is the layout chosen by the service
     * @param records whether JSONL is on
     * @param aggregates whether SQLite is on
     * @param queueCapacity event queue
     * @param segmentBytes rotation size
     * @param segmentNanos rotation age
     * @param compress whether closed segments are gzipped
     * @param retentionDays days kept
     * @param maxBytes size cap
     * @param synchronous pragma
     * @param busyTimeoutMillis busy timeout
     * @param backupHours hours between copies, 0 to disable
     * @param backupsKept copies kept
     * @param run run id
     * @param pluginVersion plugin version
     * @param model model id; only a versioned {@code jev-} or {@code kev-} id is stored, see {@link RunIds#modelLabel}
     * @param configSha256 configuration fingerprint
     * @param opener segment opener
     * @param replyCapacity reply buffer
     * @param limits caps and retentions of the learned tables and the {@code learning} section of {@code learn build}
     * @param overlayModes the global modes an {@code autoconfig apply} may not raise (M29.8, JS-20)
     */
    public record Spec(Path directory, Path database, boolean records, boolean aggregates,
                       int queueCapacity, long segmentBytes, long segmentNanos, boolean compress, int retentionDays,
                       long maxBytes, String synchronous, int busyTimeoutMillis, int backupHours, int backupsKept,
                       String run, String pluginVersion, String model, String configSha256, SegmentFiles.Opener opener,
                       int replyCapacity, StorageLimits limits, OverlayModes overlayModes) {
        /**
         * Validates the limits and the modes.
         *
         * @throws NullPointerException if {@code limits} or {@code overlayModes} is {@code null}
         */
        public Spec {
            Objects.requireNonNull(limits, "limits");
            Objects.requireNonNull(overlayModes, "overlayModes");
        }

        /**
         * A spec with the shipped global modes, {@link OverlayModes#DEFAULTS} (rule W5).
         *
         * @param directory records directory
         * @param database database file
         * @param records whether JSONL is on
         * @param aggregates whether SQLite is on
         * @param queueCapacity event queue
         * @param segmentBytes rotation size
         * @param segmentNanos rotation age
         * @param compress whether closed segments are gzipped
         * @param retentionDays days kept
         * @param maxBytes size cap
         * @param synchronous pragma
         * @param busyTimeoutMillis busy timeout
         * @param backupHours hours between copies, 0 to disable
         * @param backupsKept copies kept
         * @param run run id
         * @param pluginVersion plugin version
         * @param model model id
         * @param configSha256 configuration fingerprint
         * @param opener segment opener
         * @param replyCapacity reply buffer
         * @param limits caps and retentions of the learned tables
         */
        public Spec(Path directory, Path database, boolean records, boolean aggregates, int queueCapacity,
                    long segmentBytes, long segmentNanos, boolean compress, int retentionDays, long maxBytes,
                    String synchronous, int busyTimeoutMillis, int backupHours, int backupsKept, String run,
                    String pluginVersion, String model, String configSha256, SegmentFiles.Opener opener,
                    int replyCapacity, StorageLimits limits) {
            this(directory, database, records, aggregates, queueCapacity, segmentBytes, segmentNanos, compress,
                    retentionDays, maxBytes, synchronous, busyTimeoutMillis, backupHours, backupsKept, run,
                    pluginVersion, model, configSha256, opener, replyCapacity, limits, OverlayModes.DEFAULTS);
        }

        /**
         * A spec with the limits of a fresh installation, {@link StorageLimits#DEFAULTS} (rule W5).
         *
         * @param directory records directory
         * @param database database file
         * @param records whether JSONL is on
         * @param aggregates whether SQLite is on
         * @param queueCapacity event queue
         * @param segmentBytes rotation size
         * @param segmentNanos rotation age
         * @param compress whether closed segments are gzipped
         * @param retentionDays days kept
         * @param maxBytes size cap
         * @param synchronous pragma
         * @param busyTimeoutMillis busy timeout
         * @param backupHours hours between copies, 0 to disable
         * @param backupsKept copies kept
         * @param run run id
         * @param pluginVersion plugin version
         * @param model model id
         * @param configSha256 configuration fingerprint
         * @param opener segment opener
         * @param replyCapacity reply buffer
         */
        public Spec(Path directory, Path database, boolean records, boolean aggregates, int queueCapacity,
                    long segmentBytes, long segmentNanos, boolean compress, int retentionDays, long maxBytes,
                    String synchronous, int busyTimeoutMillis, int backupHours, int backupsKept, String run,
                    String pluginVersion, String model, String configSha256, SegmentFiles.Opener opener,
                    int replyCapacity) {
            this(directory, database, records, aggregates, queueCapacity, segmentBytes, segmentNanos, compress,
                    retentionDays, maxBytes, synchronous, busyTimeoutMillis, backupHours, backupsKept, run,
                    pluginVersion, model, configSha256, opener, replyCapacity, StorageLimits.DEFAULTS,
                    OverlayModes.DEFAULTS);
        }
    }

    /** A read or an admin command. */
    public sealed interface Work permits Work.Query, Work.Command {
        /**
         * A query.
         *
         * @param query the query
         */
        record Query(AggregateQuery query) implements Work {
        }

        /**
         * An admin command.
         *
         * @param command the command
         * @param id the id
         */
        record Command(AdminCommand command, long id) implements Work {
        }
    }

    /**
     * What {@link #close} left (T7). The root logs an unfinished close; what was left is lost.
     *
     * @param finished whether the thread ended within the bound
     * @param unapplied {@code batches=<n> events=<n>} when it did not, otherwise empty
     */
    public record CloseReport(boolean finished, String unapplied) {
        /**
         * Validates the text.
         *
         * @throws NullPointerException if {@code unapplied} is {@code null}
         */
        public CloseReport {
            Objects.requireNonNull(unapplied, "unapplied");
        }
    }

    /**
     * Starts the daemon. It keeps no trace of its operations.
     *
     * @param spec the spec
     * @param inbox where replies go
     */
    public StorageWriter(Spec spec, Inbox<InboxEntry> inbox) {
        this(spec, inbox, WriterTrace.OFF);
    }

    /**
     * Starts the daemon with the trace an ordering test of this package opts into.
     *
     * @param spec the spec
     * @param inbox where replies go
     * @param trace {@link WriterTrace#OFF}, or a bounded trace
     */
    StorageWriter(Spec spec, Inbox<InboxEntry> inbox, WriterTrace trace) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.trace = Objects.requireNonNull(trace, "trace");
        this.events = new ArrayBlockingQueue<>(spec.queueCapacity());
        this.bodyEvents = new ArrayBlockingQueue<>(spec.queueCapacity());
        this.learnBuild = new LearnBuildJob(spec.limits().learning());
        this.upkeep = new TableUpkeep(spec.limits());
        this.usableSpace = () -> usable(spec.directory());
        thread = new Thread(this::run, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The control {@code distill mode} and {@code distill build} use. The plugin passes the session control.
     *
     * @param control the control
     */
    public void useDistill(DistillControl control) {
        distillControl = control;
    }

    /**
     * The profiles read now, which {@code autoconfig apply} checks a proposal against (M29.8). The root hands a new
     * read after every read of the profiles; the writer reads the latest one on its own thread.
     *
     * @param bases the immutable read
     */
    public void useProfiles(ProfileBases bases) {
        profileBases = Objects.requireNonNull(bases, "bases");
    }

    /**
     * Queues an event. Never blocks.
     *
     * @param event the event
     * @return {@code false} when the event was dropped
     */
    public boolean offer(RecordEvent event) {
        if (!accepting.get()) {
            drop("shutdown");
            return false;
        }
        if (!events.offer(event)) {
            drop(event.type());
            return false;
        }
        return true;
    }

    /**
     * Queues a deduplicated body line. Never blocks. It is written to {@code bodies-<run>-<n>.jsonl}.
     *
     * @param event the body event
     * @return {@code false} when the event was dropped
     */
    public boolean offerBody(RecordEvent event) {
        if (!accepting.get()) {
            drop("shutdown");
            return false;
        }
        if (!bodyEvents.offer(event)) {
            drop("body");
            return false;
        }
        return true;
    }

    /**
     * Queues a batch. Never blocks.
     *
     * @param batch the batch
     * @return {@code false} when the queue is full
     */
    public boolean submit(AggregateBatch batch) {
        return batches.offer(batch);
    }

    /**
     * Queues a read or a command. Never blocks.
     *
     * @param work the work
     * @return {@code false} when the queue is full
     */
    public boolean request(Work work) {
        return reads.offer(work);
    }

    /**
     * Stops accepting, drains and returns within the timeout, without a final batch.
     *
     * @param timeoutMillis the bound
     * @return what the close left
     */
    public CloseReport close(long timeoutMillis) {
        return close(null, timeoutMillis);
    }

    /**
     * Stops accepting, drains, applies {@code direct} outside the batch queue and returns within the timeout (T7). A
     * thread still alive at the bound stops at its next turn without applying what is left, and
     * {@link #whenStopped} runs then. The run is marked clean only if the final batch was applied and no event was
     * dropped during the close.
     *
     * @param direct the final batch, or {@code null} for none (rule W5; prefer {@link #close(long)})
     * @param timeoutMillis the bound
     * @return what the close left
     */
    public CloseReport close(AggregateBatch direct, long timeoutMillis) {
        dropsBeforeClose = droppedTotal.get();
        finalBatch = direct;
        accepting.set(false);
        closing.set(true);
        try {
            thread.join(timeoutMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (!thread.isAlive()) {
            return new CloseReport(true, "");
        }
        abandoned = true;
        unapplied = "batches=" + (batches.size() + (finalBatch == null ? 0 : 1)) + " events="
                + (events.size() + bodyEvents.size());
        return new CloseReport(false, unapplied);
    }

    /**
     * Runs {@code hook} once the storage thread has ended: now when it already has, otherwise on that thread as it
     * ends. {@link StorageService} releases {@code records/.lock} here, so a second writer never opens the same files
     * while this one still runs. One hook is kept.
     *
     * @param hook what to run
     */
    public void whenStopped(Runnable hook) {
        Objects.requireNonNull(hook, "hook");
        onStop.set(hook);
        if (stopped && onStop.compareAndSet(hook, null)) {
            hook.run();
        }
    }

    /**
     * The published health.
     *
     * @return the health
     */
    public StorageHealth health() {
        return new StorageHealth(aggregateState, cause, droppedTotal.get(), batches.size(), dropped("aggregate"));
    }

    /**
     * Exception class or word that stopped the recorder, or empty.
     *
     * @return the cause
     */
    public String eventsCause() {
        return eventsCause;
    }

    /**
     * The last failure of a maintenance task (copy, checkpoint, prune, retention, compression, verdict files) or of
     * one turn, or empty. Unlike {@link StorageHealth#cause()}, it never fails a batch.
     *
     * @return the word
     */
    public String maintenanceCause() {
        return maintenanceCause;
    }

    /**
     * Drops of one type.
     *
     * @param type the type
     * @return the count
     */
    public long dropped(String type) {
        var count = dropped.get(type);
        return count == null ? 0 : count.get();
    }

    /**
     * Every counted drop.
     *
     * @return every drop
     */
    public long droppedEvents() {
        return droppedTotal.get();
    }

    /**
     * Whether today's events are paused because the size cap was hit or the disk is full.
     *
     * @return whether today's events are paused
     */
    public boolean pausedSize() {
        return diskPaused || capPaused;
    }

    /**
     * How many times a full disk has been announced. Later retries do not announce again.
     *
     * @return the announcement count
     */
    public int diskNotices() {
        return diskNotices;
    }

    /**
     * How many times a full-disk pause was released for a retry.
     *
     * @return the retry count
     */
    public int diskRetries() {
        return diskRetries;
    }

    /**
     * SQLite version read at open.
     *
     * @return SQLite version, or empty
     */
    public String sqliteVersion() {
        return sqliteVersion;
    }

    /**
     * Synchronous mode read back from SQLite.
     *
     * @return synchronous read back
     */
    public String synchronous() {
        return synchronous;
    }

    /**
     * Journal mode read back from SQLite.
     *
     * @return journal mode read back
     */
    public String journalMode() {
        return journalMode;
    }

    /**
     * Whether the records folder looks like a network file system.
     *
     * @return whether the folder looks networked
     */
    public boolean network() {
        return network;
    }

    /**
     * What a timed-out close left unapplied.
     *
     * @return text of what close left behind
     */
    public String unapplied() {
        return unapplied;
    }

    /**
     * Whether the storage thread is still alive.
     *
     * @return whether the thread is still alive
     */
    public boolean alive() {
        return thread.isAlive();
    }

    /**
     * Whether the storage thread is a daemon.
     *
     * @return whether the thread is a daemon
     */
    public boolean daemon() {
        return thread.isDaemon();
    }

    /**
     * How many times the writer loop has run, for the tests of this package.
     *
     * @return loop passes
     */
    int passes() {
        return passes.get();
    }

    /**
     * The newest words of an opt-in trace, for the tests of this package. Empty for a release writer.
     *
     * @return operation words, oldest first
     */
    List<String> trace() {
        return trace.words();
    }

    /**
     * Rows the prune of the learned tables removed under one counter, for the tests of this package.
     *
     * @param counter a counter name of {@link TableUpkeep}
     * @return the count
     */
    long pruned(String counter) {
        return upkeep.removed(counter);
    }

    /**
     * Whether a prune pass of the learned tables is still running, for the tests of this package.
     *
     * @return {@code true} while steps remain
     */
    boolean pruning() {
        return upkeep.active();
    }

    /**
     * Failures caught at the frontier of a turn, for the tests of this package.
     *
     * @return the count
     */
    long internalFailures() {
        return internalFailures.get();
    }

    /**
     * Shortens the full-disk retry for a test. Production waits five minutes.
     *
     * @param nanos the delay
     */
    void shortenDiskRetry(long nanos) {
        diskRetryNanos = nanos;
    }

    /**
     * Replaces the free-space probe of the records folder for a test.
     *
     * @param probe usable bytes
     */
    void useUsableSpace(LongSupplier probe) {
        usableSpace = Objects.requireNonNull(probe, "probe");
    }

    private void run() {
        try {
            loop();
        } finally {
            stopped = true;
            var hook = onStop.getAndSet(null);
            if (hook != null) {
                hook.run();
            }
        }
    }

    private void loop() {
        startup();
        while (true) {
            var progressed = turn();
            passes.incrementAndGet();
            if (abandoned) {
                release();
                return;
            }
            if (closing.get() && drained()) {
                finishSafely();
                return;
            }
            if (!progressed) {
                rest();
            }
        }
    }

    private void startup() {
        try {
            open();
        } catch (RuntimeException exception) {
            aggregateState = StorageState.FAILED;
            cause = internal(exception);
            closeConnections();
        }
        attempt(this::deliverVerdicts);
        attempt(this::sweepPartials);
        attempt(this::retain);
        attempt(this::startUpkeep);
        var started = System.nanoTime();
        flushAt = started;
        nextCheckpoint = started + MINUTE_NANOS;
        nextHourly = started + HOUR_NANOS;
        nextBackup = started + Math.max(1, spec.backupHours()) * HOUR_NANOS;
    }

    private boolean turn() {
        var progressed = false;
        for (var index = 0; index < STEPS; index++) {
            try {
                progressed |= step(index);
            } catch (RuntimeException exception) {
                noteInternal(exception);
            }
        }
        return progressed;
    }

    private boolean step(int step) {
        return switch (step) {
            case 0 -> {
                retryDisk(System.nanoTime());
                yield flushReplies();
            }
            case 1 -> serve();
            case 2 -> writeEvents();
            case 3 -> housekeeping();
            default -> jobs();
        };
    }

    private void attempt(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            noteInternal(exception);
        }
    }

    private boolean drained() {
        return quiet() && held == null;
    }

    private boolean quiet() {
        return events.isEmpty() && bodyEvents.isEmpty() && batches.isEmpty() && reads.isEmpty();
    }

    private boolean room() {
        return closing.get() || replies.size() < spec.replyCapacity();
    }

    private void rest() {
        if (Thread.interrupted()) {
            stopAccepting();
        }
        if (closing.get() || held != null || replies.size() >= spec.replyCapacity()) {
            LockSupport.parkNanos(closing.get() ? CLOSING_NANOS : IDLE_NANOS);
            return;
        }
        try {
            var work = reads.poll(IDLE_NANOS, TimeUnit.NANOSECONDS);
            if (work != null) {
                trace.note("read");
                handle(work);
            }
        } catch (InterruptedException exception) {
            stopAccepting();
        }
    }

    private void stopAccepting() {
        accepting.set(false);
        closing.set(true);
    }

    private void open() {
        try {
            network = network(spec.directory());
            if (spec.records()) {
                var eventsDir = spec.directory().resolve("events");
                segments = new SegmentFiles(eventsDir, spec.run(), spec.opener(), spec.segmentBytes(),
                        spec.segmentNanos(), "events");
                bodySegments = new SegmentFiles(eventsDir, spec.run(), spec.opener(), spec.segmentBytes(),
                        spec.segmentNanos(), "bodies");
            }
            if (!spec.aggregates()) {
                aggregateState = StorageState.DISABLED;
                return;
            }
            openAggregates();
        } catch (NoClassDefFoundError error) {
            aggregateState = StorageState.DISABLED;
            cause = "DRIVER_MISSING";
        } catch (SQLException exception) {
            aggregateState = StorageState.FAILED;
            cause = exception.getClass().getSimpleName();
        }
    }

    private void openAggregates() throws SQLException {
        var migrated = migrate();
        if (migrated.state() == StorageState.DISABLED) {
            aggregateState = StorageState.DISABLED;
            cause = migrated.cause();
            return;
        }
        if (migrated.state() == StorageState.READ_ONLY && !corruptCause(migrated.cause())) {
            aggregateState = migrated.state();
            cause = migrated.cause();
            read = SqliteConnections.readOnly(spec.database());
            return;
        }
        if (!usable(migrated)) {
            return;
        }
        var dirty = dirtyRun();
        insertRun();
        aggregateState = StorageState.READY;
        if (dirty && cause.isEmpty()) {
            cause = "DIRTY_SHUTDOWN";
        }
        loadOutcomes();
    }

    private boolean usable(SchemaMigrator.Result migrated) throws SQLException {
        if (migrated.state() != StorageState.READY) {
            return recovered();
        }
        if (!attach()) {
            closeConnections();
            return false;
        }
        if (quickCheck()) {
            return true;
        }
        closeConnections();
        return recovered();
    }

    private void deliverVerdicts() {
        reply(new StorageReply.VerdictsLoaded(verdictRows(VerdictFiles.MAX_FILES), System.nanoTime()));
    }

    private List<StorageReply.GateVerdictRow> verdictRows(int limit) {
        var rows = new ArrayList<StorageReply.GateVerdictRow>();
        try {
            var loaded = VerdictFiles.load(spec.directory().resolve("eval/verdicts"));
            rows.addAll(loaded.rows());
            if (loaded.skipped() > 0) {
                maintenanceCause = "VERDICTS_SKIPPED";
            }
        } catch (IOException exception) {
            noteMaintenanceIo(exception, "VERDICTS_UNREADABLE");
        }
        if (read != null && (aggregateState == StorageState.READY || aggregateState == StorageState.READ_ONLY)) {
            try {
                rows.addAll(GateVerdictSql.load(read, limit));
            } catch (SQLException exception) {
                cause = sqlCause(exception);
            }
        }
        return List.copyOf(rows);
    }

    private void finishSafely() {
        try {
            finish();
        } catch (RuntimeException exception) {
            noteInternal(exception);
            closeConnections();
        }
    }

    private void finish() {
        var clean = applyFinal();
        flushReplies();
        clean &= droppedTotal.get() == dropsBeforeClose;
        appendStop(clean);
        flushFiles();
        closeSegments(segments, true);
        closeSegments(bodySegments, true);
        while (!compressing.isEmpty()) {
            compressStep();
        }
        markStopped(clean);
        checkpoint("TRUNCATE");
        optimize();
        closeConnections();
    }

    private boolean applyFinal() {
        var batch = finalBatch;
        if (batch == null) {
            return true;
        }
        finalBatch = null;
        if (aggregateState == StorageState.READY) {
            return applyWithRetry(batch);
        }
        reply(failed(batch, cause.isEmpty() ? aggregateState.name() : cause));
        return !spec.aggregates();
    }

    /** Stops after a close that timed out: closes the files and connections and applies nothing more. */
    private void release() {
        appendStop(false);
        flushFiles();
        closeSegments(segments, false);
        closeSegments(bodySegments, false);
        for (var job : compressing) {
            job.abandon();
        }
        compressing.clear();
        closeConnections();
    }

    private void appendStop(boolean clean) {
        if (segments == null) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("clean", clean ? "true" : "false");
        var event = new RecordEvent("run_stop", spec.run(), System.currentTimeMillis(), System.nanoTime(), 0L, "",
                fields);
        try {
            segments.append(RecordLine.format(event), event.tMs(), System.nanoTime());
        } catch (IOException exception) {
            noteSegmentIo(exception);
        }
    }

    private void markStopped(boolean clean) {
        if (write == null) {
            return;
        }
        try (var statement = write.prepareStatement(
                "UPDATE runs SET stopped_epoch_ms = ?, clean_shutdown = ? WHERE run = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setInt(2, clean ? 1 : 0);
            statement.setString(3, spec.run());
            statement.executeUpdate();
        } catch (SQLException exception) {
            cause = "CLOSE_FAILED";
        }
    }

    private void insertRun() throws SQLException {
        try (var statement = write.prepareStatement(
                "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                        + "VALUES (?,?,0,?,?,?,?)")) {
            statement.setString(1, spec.run());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, spec.pluginVersion());
            statement.setString(4, RunIds.modelLabel(spec.model()));
            statement.setString(5, spec.configSha256());
            statement.setString(6, sqliteVersion);
            statement.executeUpdate();
        }
    }

    /** Whether the newest earlier run did not stop cleanly. An older dirty run no longer counts. */
    private boolean dirtyRun() throws SQLException {
        try (var statement = write.createStatement();
             var result = statement.executeQuery(
                     "SELECT clean_shutdown FROM runs ORDER BY started_epoch_ms DESC, rowid DESC LIMIT 1")) {
            return result.next() && result.getInt(1) == 0;
        }
    }

    private SchemaMigrator.Result migrate() {
        return SchemaMigrator.open(spec.database(), SchemaMigrator.bundled(), spec.synchronous(),
                spec.busyTimeoutMillis(), spec.pluginVersion(), System.currentTimeMillis(),
                spec.directory().resolve("backups"));
    }

    private boolean attach() throws SQLException {
        write = SqliteConnections.open(spec.database(), spec.synchronous(), spec.busyTimeoutMillis());
        read = SqliteConnections.open(spec.database(), spec.synchronous(), spec.busyTimeoutMillis());
        var pragmas = SqlitePragmas.read(write);
        journalMode = pragmas.getOrDefault("journal_mode", "");
        synchronous = pragmas.getOrDefault("synchronous", "");
        try (var statement = write.createStatement();
             var result = statement.executeQuery("select sqlite_version()")) {
            if (result.next()) {
                sqliteVersion = result.getString(1);
            }
        }
        var assessed = SqliteAvailability.classify(sqliteVersion);
        if (assessed.state() != StorageState.READY) {
            aggregateState = assessed.state();
            cause = assessed.cause();
            return false;
        }
        return true;
    }

    private boolean recovered() {
        if (recover()) {
            return true;
        }
        if (aggregateState != StorageState.FAILED && aggregateState != StorageState.READ_ONLY) {
            aggregateState = StorageState.FAILED;
            cause = "CORRUPT";
        }
        return false;
    }

    private boolean recover() {
        try {
            closeConnections();
            var outcome = RecoveryPolicy.recover(spec.database(), spec.directory().resolve("backups"));
            var migrated = migrate();
            if (migrated.state() != StorageState.READY) {
                aggregateState = migrated.state();
                cause = migrated.cause();
                if (migrated.state() == StorageState.READ_ONLY && !corruptCause(migrated.cause())) {
                    read = SqliteConnections.readOnly(spec.database());
                }
                return false;
            }
            if (!attach() || !quickCheck()) {
                aggregateState = StorageState.FAILED;
                cause = "CORRUPT";
                return false;
            }
            cause = outcome == RecoveryPolicy.Outcome.RESTORED ? "RESTORED" : "EMPTY";
            return true;
        } catch (IOException | SQLException exception) {
            aggregateState = StorageState.FAILED;
            cause = "CORRUPT";
            return false;
        }
    }

    private static boolean corruptCause(String word) {
        return "SQLITE_CORRUPT".equals(word) || "SQLITE_NOTADB".equals(word);
    }

    private void closeSegments(SegmentFiles files, boolean compress) {
        if (files == null) {
            return;
        }
        try {
            for (var path : files.close()) {
                if (compress && spec.compress()) {
                    compressing.add(SegmentCompressor.start(path));
                }
            }
        } catch (IOException exception) {
            noteSegmentIo(exception);
        }
    }

    private void noteStats(long now) {
        if (!spec.records() || segments == null) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("dropped", Long.toString(droppedTotal.get()));
        fields.put("bytes", Long.toString(bytesTotal));
        events.offer(new RecordEvent("writer_stats", spec.run(), System.currentTimeMillis(), now, 0, "", fields));
    }

    private boolean quickCheck() {
        if (write == null) {
            return false;
        }
        try (var statement = write.createStatement();
             var result = statement.executeQuery("PRAGMA quick_check")) {
            return result.next() && "ok".equalsIgnoreCase(result.getString(1));
        } catch (SQLException exception) {
            return false;
        }
    }

    private void loadOutcomes() {
        if (read == null || replies.size() >= spec.replyCapacity()) {
            return;
        }
        try {
            replies.add(outcomes(List.of()));
            trace.note("load");
        } catch (SQLException exception) {
            replies.add(new StorageReply.QueryFailed(exception.getClass().getSimpleName(), System.nanoTime()));
        }
    }

    private StorageReply.OutcomeStatsLoaded outcomes(List<String> profiles) throws SQLException {
        var previous = read.getAutoCommit();
        read.setAutoCommit(false);
        try {
            var loaded = OutcomeStatsSql.load(read, profiles);
            var applied = lastApplied();
            read.commit();
            return new StorageReply.OutcomeStatsLoaded(loaded.rows(), applied, loaded.truncated(), System.nanoTime());
        } catch (SQLException | RuntimeException exception) {
            read.rollback();
            throw exception;
        } finally {
            read.setAutoCommit(previous);
        }
    }

    private boolean serve() {
        if (!room()) {
            return false;
        }
        var progressed = false;
        if (held != null) {
            var work = held;
            held = null;
            handle(work);
            progressed = true;
        }
        progressed |= drainReads();
        progressed |= drainBatches();
        return progressed;
    }

    private boolean drainReads() {
        var handled = false;
        Work work;
        while (room() && (work = reads.poll()) != null) {
            trace.note("read");
            handle(work);
            handled = true;
        }
        return handled;
    }

    private boolean drainBatches() {
        var handled = false;
        AggregateBatch batch;
        while (room() && (batch = batches.poll()) != null) {
            trace.note("batch");
            applyWithRetry(batch);
            handled = true;
        }
        return handled;
    }

    private void handle(Work work) {
        switch (work) {
            case Work.Query query -> query(query.query());
            case Work.Command command -> command(command);
        }
    }

    private void query(AggregateQuery query) {
        if (query instanceof AggregateQuery.VerdictQuery verdicts) {
            reply(new StorageReply.VerdictsLoaded(verdictRows(verdicts.limit()), System.nanoTime()));
            return;
        }
        if (read == null) {
            reply(new StorageReply.QueryFailed(cause.isEmpty() ? "DISABLED" : cause, System.nanoTime()));
            return;
        }
        try {
            reply(answer(query));
        } catch (SQLException exception) {
            reply(new StorageReply.QueryFailed(sqlCause(exception), System.nanoTime()));
        } catch (RuntimeException exception) {
            reply(new StorageReply.QueryFailed(internal(exception), System.nanoTime()));
        }
    }

    private StorageReply answer(AggregateQuery query) throws SQLException {
        return switch (query) {
            case AggregateQuery.OutcomeStatsQuery outcomes -> outcomes(outcomes.profiles());
            case AggregateQuery.DifficultyStatsQuery difficulty -> new StorageReply.DifficultyStatsLoaded(
                    DifficultyStatsSql.load(read, difficulty.profile()), System.nanoTime());
            case AggregateQuery.VerdictQuery verdicts -> new StorageReply.VerdictsLoaded(
                    verdictRows(verdicts.limit()), System.nanoTime());
            case AggregateQuery.SubjectMemoryQuery memory -> subjectMemory(memory);
            case AggregateQuery.ValueStatsQuery values -> valuePage(values);
            case AggregateQuery.ArtifactsQuery ignored -> new StorageReply.ArtifactsLoaded(ArtifactSql.list(read),
                    System.nanoTime());
            case AggregateQuery.ChoiceStatsQuery choices -> new StorageReply.ChoiceStatsPage(
                    ChoiceStatSql.page(read, choices.profileFilter(), choices.sinceWeek(), choices.pageRows()),
                    System.nanoTime());
            case AggregateQuery.AgreementStatsQuery agreements -> new StorageReply.AgreementStatsPage(
                    AgreementStatSql.page(read, agreements.artifact(), agreements.pageRows()), System.nanoTime());
        };
    }

    private StorageReply valuePage(AggregateQuery.ValueStatsQuery values) throws SQLException {
        var page = ValueStatSql.page(read, values.profileFilter(), values.sinceWeek(), values.pageRows(),
                values.cursor());
        return new StorageReply.ValueStatsPage(page.rows(), page.nextCursor(), lastApplied(), System.nanoTime());
    }

    private void command(Work.Command command) {
        switch (command.command()) {
            case AdminCommand.DistillBuild build -> startDistillBuild(command.id(), build);
            case AdminCommand.LearnBuild build -> startLearnBuild(command.id(), build);
            default -> runAdmin(command);
        }
    }

    private void startDistillBuild(long id, AdminCommand.DistillBuild build) {
        if (distillTable.begun() && !distillTable.done()) {
            reply(admin(id, false, "busy"));
            return;
        }
        distillCommand = id;
        distillTable.begin(build.profileFilter());
    }

    /**
     * Runs one admin command. A refused proposal or path answers with its reason; any other failure with its class.
     * A verdict just imported is delivered again with the others, so the gates see it without a restart (CAR-08, W8).
     */
    private void runAdmin(Work.Command command) {
        try {
            var detail = AdminTasks.run(command.command(), spec.directory(), spec.database(), write, read,
                    spec.backupsKept(), distillControl, spec.overlayModes(), profileBases);
            reply(admin(command.id(), true, detail));
            if (command.command() instanceof AdminCommand.VerdictImport) {
                deliverVerdicts();
            }
        } catch (AdminRefusal refusal) {
            reply(admin(command.id(), false, refusal.getMessage()));
        } catch (IOException | SQLException | RuntimeException exception) {
            reply(admin(command.id(), false, exception.getClass().getSimpleName()));
        }
    }

    private void startLearnBuild(long id, AdminCommand.LearnBuild build) {
        if (read == null || write == null || aggregateState != StorageState.READY) {
            reply(admin(id, false, cause.isEmpty() ? "DISABLED" : cause));
            return;
        }
        if (learnBuild.running()) {
            reply(admin(id, false, "busy"));
            return;
        }
        try {
            learnBuild.start(id, build.profileFilter(), lastApplied());
        } catch (SQLException exception) {
            reply(admin(id, false, sqlCause(exception)));
        }
    }

    private StorageReply.AdminResult admin(long id, boolean ok, String detail) {
        return new StorageReply.AdminResult(id, ok, detail, System.nanoTime());
    }

    private boolean jobs() {
        var progressed = distillStep();
        progressed |= learnStep();
        progressed |= upkeepStep();
        return progressed;
    }

    private boolean distillStep() {
        if (!distillTable.begun() || distillTable.done()) {
            return false;
        }
        try {
            distillTable.turn(write, 2000);
        } catch (SQLException | RuntimeException exception) {
            distillTable.fail();
            reply(admin(distillCommand, false, exception.getClass().getSimpleName()));
            return true;
        }
        if (distillTable.done()) {
            reply(admin(distillCommand, true, distillTable.note()));
        }
        return true;
    }

    private boolean learnStep() {
        if (!learnBuild.running()) {
            return false;
        }
        var id = learnBuild.commandId();
        if (read == null || write == null || aggregateState != StorageState.READY) {
            learnBuild.stop();
            reply(admin(id, false, cause.isEmpty() ? "DISABLED" : cause));
            return true;
        }
        try {
            learnBuild.step(read, write, spec.directory().resolveSibling("learn")).ifPresent(detail -> reply(
                    admin(id, true, detail + " " + Thread.currentThread().getName())));
        } catch (IOException | SQLException | RuntimeException exception) {
            learnBuild.stop();
            reply(admin(id, false, exception.getClass().getSimpleName()));
        }
        return true;
    }

    private boolean upkeepStep() {
        if (!upkeep.active()) {
            return false;
        }
        if (write == null || aggregateState != StorageState.READY) {
            upkeep.stop();
            return false;
        }
        try {
            return upkeep.step(write);
        } catch (SQLException exception) {
            noteSql(exception);
            maintenanceCause = "PRUNE_FAILED";
            return false;
        }
    }

    private void startUpkeep() {
        if (write != null && aggregateState == StorageState.READY) {
            upkeep.start(System.currentTimeMillis());
        }
    }

    private boolean applyWithRetry(AggregateBatch batch) {
        if (write == null || aggregateState != StorageState.READY) {
            reply(failed(batch, cause.isEmpty() ? "READ_ONLY" : cause));
            return false;
        }
        SQLException last = null;
        for (var attempt = 0; attempt < 2; attempt++) {
            try {
                BatchApply.apply(write, batch, spec.run());
                reply(appliedReply(batch));
                return true;
            } catch (SQLException exception) {
                last = exception;
            } catch (RuntimeException exception) {
                drop("aggregate");
                reply(failed(batch, internal(exception)));
                return false;
            }
        }
        drop("aggregate");
        noteSql(last);
        reply(failed(batch, sqlCause(last)));
        return false;
    }

    private static StorageReply.BatchFailed failed(AggregateBatch batch, String word) {
        return new StorageReply.BatchFailed(batch.batchId(), word, System.nanoTime());
    }

    private StorageReply appliedReply(AggregateBatch batch) {
        if (batch.memory().isEmpty()) {
            return new StorageReply.BatchApplied(batch.batchId(), System.nanoTime());
        }
        var records = new LinkedHashMap<java.util.UUID, byte[]>();
        for (var delta : batch.memory()) {
            if (delta.delete()) {
                records.put(delta.subject(), new byte[0]);
                continue;
            }
            try {
                var loaded = PlayerMemorySql.load(write, delta.subject());
                records.put(delta.subject(), loaded.orElse(new byte[0]));
            } catch (SQLException exception) {
                records.put(delta.subject(), delta.record());
            }
        }
        return new StorageReply.MemoryApplied(batch.batchId(), records, System.nanoTime());
    }

    private StorageReply subjectMemory(AggregateQuery.SubjectMemoryQuery query) throws SQLException {
        var loaded = PlayerMemorySql.load(read, query.subject());
        var newer = loaded.filter(PlayerMemoryCodec::newer).isPresent();
        var record = newer ? java.util.Optional.<byte[]>empty() : loaded;
        return new StorageReply.SubjectMemoryLoaded(query.subject(), record, lastApplied(), newer,
                System.nanoTime());
    }

    private long lastApplied() throws SQLException {
        try (var statement = read.prepareStatement("SELECT last_applied_batch FROM runs WHERE run = ?")) {
            statement.setString(1, spec.run());
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private void noteSql(SQLException exception) {
        var code = exception.getErrorCode();
        if (code == 13 || contains(exception, "SQLITE_FULL")) {
            pauseDisk();
        } else if (code == 11 || code == 26 || contains(exception, "SQLITE_CORRUPT") || contains(exception, "SQLITE_NOTADB")) {
            aggregateState = StorageState.FAILED;
            cause = code == 26 ? "SQLITE_NOTADB" : "SQLITE_CORRUPT";
            upkeep.stop();
            learnBuild.stop();
        }
    }

    private static boolean contains(SQLException exception, String token) {
        return exception.getMessage() != null && exception.getMessage().contains(token);
    }

    private static String sqlCause(SQLException exception) {
        if (exception.getErrorCode() == 13) {
            return "SQLITE_FULL";
        }
        if (exception.getErrorCode() == 11) {
            return "SQLITE_CORRUPT";
        }
        return exception.getClass().getSimpleName();
    }

    private static String internal(RuntimeException exception) {
        return "INTERNAL:" + exception.getClass().getSimpleName();
    }

    private void noteInternal(RuntimeException exception) {
        internalFailures.incrementAndGet();
        maintenanceCause = internal(exception);
    }

    private boolean writeEvents() {
        var moved = writeQueue(events, segments);
        moved |= writeQueue(bodyEvents, bodySegments);
        return moved;
    }

    private boolean writeQueue(ArrayBlockingQueue<RecordEvent> queue, SegmentFiles files) {
        if (files == null) {
            var had = !queue.isEmpty();
            queue.clear();
            return had;
        }
        var moved = 0;
        while (moved < EVENT_BATCH) {
            var event = queue.peek();
            if (event == null || !move(queue, files, event)) {
                break;
            }
            moved++;
        }
        compressClosed(files);
        return moved > 0;
    }

    /** Writes or drops the head event. {@code false} when a write failed and the event stays for a retry. */
    private boolean move(ArrayBlockingQueue<RecordEvent> queue, SegmentFiles files, RecordEvent event) {
        if (eventsFailed || pausedSize()) {
            queue.poll();
            drop(eventsFailed ? "failed" : "paused");
            return true;
        }
        String line;
        try {
            line = RecordLine.format(event);
        } catch (RuntimeException exception) {
            queue.poll();
            drop("malformed");
            return true;
        }
        try {
            files.append(line, event.tMs(), System.nanoTime());
        } catch (IOException exception) {
            noteSegmentIo(exception);
            return false;
        }
        queue.poll();
        bytesTotal += line.length() + 1L;
        trace.note("event");
        return true;
    }

    private void compressClosed(SegmentFiles files) {
        for (var path : files.pollClosed()) {
            if (!spec.compress()) {
                continue;
            }
            try {
                compressing.add(SegmentCompressor.start(path));
            } catch (IOException exception) {
                noteMaintenanceIo(exception, "COMPRESS_FAILED");
            }
        }
    }

    /** A segment write or flush failed: the segment is abandoned and three failures in a minute stop the events. */
    private void noteSegmentIo(IOException exception) {
        if (segments != null) {
            segments.abandon();
        }
        if (bodySegments != null) {
            bodySegments.abandon();
        }
        if (diskFull(exception)) {
            pauseDisk();
            return;
        }
        var now = System.nanoTime();
        ioTimes.removeIf(time -> now - time > MINUTE_NANOS);
        ioTimes.add(now);
        if (ioTimes.size() >= 3) {
            eventsFailed = true;
            eventsCause = "FAILED";
        }
    }

    /** A maintenance task failed: a full disk pauses as usual; anything else is only noted. */
    private void noteMaintenanceIo(IOException exception, String word) {
        if (diskFull(exception)) {
            pauseDisk();
            return;
        }
        maintenanceCause = word;
    }

    private void clearMaintenance(String word) {
        if (word.equals(maintenanceCause)) {
            maintenanceCause = "";
        }
    }

    /**
     * Whether a failure means the disk is full. The message of the operating system is translated with its locale, so
     * the free space of the records folder decides too: less than one segment, or 64 MiB, is full.
     */
    private boolean diskFull(IOException exception) {
        var message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("no space")
                || usableSpace.getAsLong() < Math.min(spec.segmentBytes(), FULL_DISK_BYTES);
    }

    private void pauseDisk() {
        if (aggregateState == StorageState.READY) {
            aggregateState = StorageState.READ_ONLY;
        }
        if (diskNotices == 0) {
            diskNotices = 1;
        }
        cause = "DISK_FULL";
        diskPaused = true;
        if (diskPausedAt == Long.MIN_VALUE) {
            diskPausedAt = System.nanoTime();
        }
    }

    private void retryDisk(long now) {
        if (!diskPaused || diskPausedAt == Long.MIN_VALUE) {
            return;
        }
        if (now - diskPausedAt < diskRetryNanos) {
            return;
        }
        diskPaused = false;
        diskPausedAt = Long.MIN_VALUE;
        diskRetries++;
        if (aggregateState == StorageState.READ_ONLY && "DISK_FULL".equals(cause)) {
            aggregateState = StorageState.READY;
            cause = "";
        }
    }

    private boolean housekeeping() {
        var now = System.nanoTime();
        if (now - flushAt >= FLUSH_NANOS || quiet()) {
            flushFiles();
            flushAt = now;
        }
        compressStep();
        if (now >= nextCheckpoint) {
            checkpoint("PASSIVE");
            noteStats(now);
            nextCheckpoint = now + MINUTE_NANOS;
        }
        if (now >= nextHourly) {
            checkpoint("TRUNCATE");
            optimize();
            retain();
            startUpkeep();
            nextHourly = now + HOUR_NANOS;
        }
        if (spec.backupHours() > 0 && now >= nextBackup && write != null) {
            backup();
            nextBackup = now + spec.backupHours() * HOUR_NANOS;
        }
        return false;
    }

    /** Flushes the open event segment and the open body segment (M21.2: every second or when idle). */
    private void flushFiles() {
        try {
            if (segments != null) {
                segments.flush();
            }
            if (bodySegments != null) {
                bodySegments.flush();
            }
        } catch (IOException exception) {
            noteSegmentIo(exception);
        }
    }

    private void compressStep() {
        if (compressing.isEmpty()) {
            return;
        }
        var job = compressing.getFirst();
        try {
            if (job.step()) {
                compressing.removeFirst();
            }
        } catch (IOException exception) {
            compressing.removeFirst();
            job.abandon();
            noteMaintenanceIo(exception, "COMPRESS_FAILED");
        }
    }

    private void retain() {
        if (segments == null) {
            return;
        }
        try {
            var root = spec.directory().resolve("events");
            var today = LocalDate.now(ZoneOffset.UTC).toString();
            var plan = RetentionPolicy.plan(EventDays.sizes(root), today, spec.retentionDays(), spec.maxBytes());
            for (var day : plan.delete()) {
                EventDays.delete(root.resolve(day));
            }
            capPaused = plan.pausedSize();
            clearMaintenance("RETENTION_FAILED");
        } catch (IOException exception) {
            noteMaintenanceIo(exception, "RETENTION_FAILED");
        }
    }

    /** Deletes the {@code .gz.partial} files a stopped compression left (M21.3). */
    private void sweepPartials() {
        if (!spec.records()) {
            return;
        }
        try {
            SegmentCompressor.deletePartials(spec.directory().resolve("events"));
        } catch (IOException exception) {
            noteMaintenanceIo(exception, "COMPRESS_FAILED");
        }
    }

    private void backup() {
        try {
            var directory = spec.directory().resolve("backups");
            Files.createDirectories(directory);
            BackupFiles.vacuumInto(write, BackupFiles.next(directory, Instant.now()));
            BackupFiles.trim(directory, spec.backupsKept());
            clearMaintenance("BACKUP_FAILED");
        } catch (IOException | SQLException exception) {
            maintenanceCause = "BACKUP_FAILED";
        }
    }

    private void checkpoint(String mode) {
        if (write == null) {
            return;
        }
        try (var statement = write.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(" + mode + ")");
            clearMaintenance("CHECKPOINT_FAILED");
        } catch (SQLException exception) {
            maintenanceCause = "CHECKPOINT_FAILED";
        }
    }

    private void optimize() {
        if (write == null) {
            return;
        }
        try (var statement = write.createStatement()) {
            statement.execute("PRAGMA optimize");
            clearMaintenance("OPTIMIZE_FAILED");
        } catch (SQLException exception) {
            maintenanceCause = "OPTIMIZE_FAILED";
        }
    }

    private void closeConnections() {
        close(write);
        close(read);
        write = null;
        read = null;
    }

    private static void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            if (exception.getErrorCode() < 0) {
                return;
            }
        }
    }

    private boolean flushReplies() {
        var delivered = false;
        while (true) {
            var next = replies.peekFirst();
            if (next == null || !inbox.offerStorage(next)) {
                return delivered;
            }
            replies.pollFirst();
            delivered = true;
        }
    }

    private void reply(StorageReply reply) {
        if (replies.size() >= spec.replyCapacity()) {
            return;
        }
        replies.addLast(reply);
    }

    private void drop(String type) {
        dropped.computeIfAbsent(type, ignored -> new AtomicLong()).incrementAndGet();
        droppedTotal.incrementAndGet();
    }

    private static boolean network(Path directory) {
        try {
            var type = Files.getFileStore(directory).type().toLowerCase(Locale.ROOT);
            return type.contains("nfs") || type.contains("cifs") || type.contains("smbfs") || type.contains("sshfs");
        } catch (IOException exception) {
            return false;
        }
    }

    private static long usable(Path directory) {
        try {
            return Files.getFileStore(directory).getUsableSpace();
        } catch (IOException exception) {
            return Long.MAX_VALUE;
        }
    }
}
