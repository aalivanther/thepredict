package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.AggregateCache;
import akyrion.thepredict.engine.AggregateQuery;
import akyrion.thepredict.engine.AggregateStore;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.StorageHealth;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import akyrion.thepredict.engine.StorageStatus;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Facade of the records and the aggregate store (M21.2, M21.9). The server thread calls it. The daemon is created
 * only after {@code records/.lock} is held. With records and aggregates both off it serves only the gate verdicts
 * (T1-ter): it reads {@code eval/verdicts/} at the start and takes {@code eval verdict import}, and its status says
 * {@link #VERDICTS_ONLY}. {@link #close} writes {@code run_stop} before it stops accepting.
 */
public final class StorageService implements AggregateStore, StorageControl {
    /** How long a watcher may wait. */
    public static final long WATCHER_NANOS = 30_000_000_000L;
    /** Cause shown by a writer that only serves the gate verdicts, so the commands can still reach it (CAR-08). */
    public static final String VERDICTS_ONLY = "VERDICTS_ONLY";

    private final String level;
    private final String cause;
    private final StorageWriter writer;
    private final RecordTap tap;
    private final RecordsLock lock;
    private final AggregateCache cache;
    private final Runnable clearCache;
    private final boolean aggregates;
    private final boolean verdictsOnly;
    private volatile String closeNote = "";
    private final List<Watch> watchers = new ArrayList<>();
    private long nextId = 1;
    private java.util.function.Consumer<String> memoryReset;

    private StorageService(String level, String cause, StorageWriter writer, RecordTap tap, RecordsLock lock,
                           AggregateCache cache, Runnable clearCache, boolean aggregates, boolean verdictsOnly) {
        this.level = level;
        this.cause = cause;
        this.writer = writer;
        this.tap = tap;
        this.lock = lock;
        this.cache = cache;
        this.clearCache = clearCache;
        this.aggregates = aggregates;
        this.verdictsOnly = verdictsOnly;
    }

    /**
     * A service with neither records nor aggregates. No daemon.
     *
     * @param levelWord configured record level
     * @return the service
     */
    public static StorageService idle(String levelWord) {
        return new StorageService(levelWord, "", null, null, null, null, null, false, false);
    }

    /**
     * Opens storage for one plugin start. The root has already translated the configuration into {@code spec}. With
     * records on, the first line is {@code run_start} (M21.1).
     *
     * @param spec writer specification; its directory is the records folder
     * @param level record level
     * @param bodies body policy
     * @param questionDetail {@code decision.questionDetail}, for {@code run_start}
     * @param answerCache {@code decision.answerCache}, for {@code run_start}
     * @param inbox engine inbox
     * @param cache aggregate cache, for flush
     * @param clearCache drops the answer cache on the server thread
     * @param ticks server tick
     * @return the service; without a daemon when the lock is held
     */
    public static StorageService open(StorageWriter.Spec spec, RecordLevel level, RecordBodies bodies,
                                      QuestionDetail questionDetail, AnswerCacheMode answerCache,
                                      Inbox<InboxEntry> inbox, AggregateCache cache, Runnable clearCache,
                                      LongSupplier ticks) {
        Objects.requireNonNull(questionDetail, "questionDetail");
        Objects.requireNonNull(answerCache, "answerCache");
        var word = level.name().toLowerCase(Locale.ROOT);
        RecordsLock held;
        try {
            held = RecordsLock.tryAcquire(spec.directory()).orElse(null);
        } catch (IOException exception) {
            return new StorageService(word, "LOCK_FAILED", null, null, null, cache, clearCache, false, false);
        }
        if (held == null) {
            return new StorageService(word, "LOCK_HELD", null, null, null, cache, clearCache, false, false);
        }
        var writer = new StorageWriter(spec, inbox);
        RecordTap tap = null;
        if (spec.records()) {
            tap = new RecordTap(level, bodies, spec.run(), Clock.systemUTC(), ticks, System::nanoTime, writer::offer,
                    writer::offerBody);
            tap.runStart(spec.pluginVersion(), spec.model(), spec.configSha256(), questionDetail, answerCache);
        }
        return new StorageService(word, "", writer, tap, held, cache, clearCache, spec.aggregates(),
                !spec.records() && !spec.aggregates());
    }

    /**
     * The records tap, or null when records are off. Kept for the root while it moves to {@link #recordTap()} (rule
     * W5).
     *
     * @return the tap
     */
    public RecordTap tap() {
        return tap;
    }

    /**
     * The records tap.
     *
     * @return the tap, empty when records are off
     */
    public Optional<RecordTap> recordTap() {
        return Optional.ofNullable(tap);
    }

    /**
     * Whether aggregates were opened and the store may be attached.
     *
     * @return {@code true} when a writer is serving SQLite
     */
    public boolean aggregatesAttached() {
        return writer != null && aggregates;
    }

    /**
     * The writer, for the tests of this package.
     *
     * @return the writer, or null
     */
    StorageWriter writer() {
        return writer;
    }

    /**
     * Passes the distill control to the writer. Commands no longer read a static.
     *
     * @param control the control
     */
    public void useDistill(akyrion.thepredict.engine.distill.DistillControl control) {
        if (writer != null) {
            writer.useDistill(control);
        }
    }

    /**
     * Hands the writer the profiles read now, which {@code autoconfig apply} checks a proposal against (M29.8). The
     * root calls it after every read of the profiles. Without a writer it does nothing.
     *
     * @param bases the immutable read
     */
    public void useProfiles(ProfileBases bases) {
        Objects.requireNonNull(bases, "bases");
        if (writer != null) {
            writer.useProfiles(bases);
        }
    }

    /**
     * Stops accepting, applies {@code direct} and writes {@code run_stop} (T7). The lock is released when the storage
     * thread ends: at once after a close within the bound, otherwise when the late thread stops, so a second service
     * on the same folder gets {@code LOCK_HELD} meanwhile. The caller logs an unfinished report: what it names is lost.
     *
     * @param direct the final batch, or {@code null} for none (rule W5; prefer {@link #close(long)})
     * @param timeoutMillis bound of the join
     * @return what the close left
     */
    public StorageWriter.CloseReport close(AggregateBatch direct, long timeoutMillis) {
        if (writer == null) {
            releaseLock();
            return new StorageWriter.CloseReport(true, "");
        }
        var report = writer.close(direct, timeoutMillis);
        if (report.finished()) {
            releaseLock();
        } else {
            writer.whenStopped(this::releaseLock);
        }
        return report;
    }

    /**
     * Stops without a final batch, as {@link #close(AggregateBatch, long)}.
     *
     * @param timeoutMillis bound of the join
     * @return what the close left
     */
    public StorageWriter.CloseReport close(long timeoutMillis) {
        return close(null, timeoutMillis);
    }

    private void releaseLock() {
        if (lock == null) {
            return;
        }
        try {
            lock.close();
        } catch (IOException exception) {
            closeNote = "LOCK_CLOSE";
        }
    }

    /**
     * Drops watchers older than {@link #WATCHER_NANOS}.
     *
     * @param nanos monotonic time
     */
    public void expire(long nanos) {
        List<Watch> expired;
        synchronized (watchers) {
            expired = expiredLocked(nanos);
        }
        timedOut(expired, nanos);
    }

    /**
     * Delivers an admin result on the server thread.
     *
     * @param result the result
     */
    public void deliver(StorageReply.AdminResult result) {
        Watch found;
        synchronized (watchers) {
            found = null;
            for (var watch : watchers) {
                if (watch.id() == result.commandId()) {
                    found = watch;
                }
            }
            if (found != null) {
                watchers.remove(found);
            }
        }
        if (found != null) {
            found.watcher().accept(result);
        }
    }

    @Override
    public StorageStatus status() {
        if (writer == null) {
            var shownCause = closeNote.isEmpty() ? cause : closeNote;
            return new StorageStatus(StorageState.DISABLED, shownCause, level, 0, 0, 0, "", "", "", false, false,
                    shownCause);
        }
        var health = published();
        var shown = shownCause(health);
        var flight = cache == null ? health.batchesInFlight() : cache.inFlight();
        return new StorageStatus(health.state(), shown, level, health.droppedEvents(), flight, health.failedBatches(),
                writer.sqliteVersion(), writer.synchronous(), writer.journalMode(), writer.pausedSize(),
                writer.network(), writer.eventsCause());
    }

    /**
     * The first cause that applies: the close, the store, the open, the last maintenance failure, and last of all
     * {@link #VERDICTS_ONLY} for a writer that only serves the verdicts.
     */
    private String shownCause(StorageHealth health) {
        if (!closeNote.isEmpty()) {
            return closeNote;
        }
        if (!health.cause().isEmpty()) {
            return health.cause();
        }
        if (!cause.isEmpty()) {
            return cause;
        }
        var maintenance = writer.maintenanceCause();
        return maintenance.isEmpty() && verdictsOnly ? VERDICTS_ONLY : maintenance;
    }

    @Override
    public boolean atCapacity() {
        synchronized (watchers) {
            return watchers.size() >= MAX_WATCHERS;
        }
    }

    @Override
    public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
        List<Watch> expired;
        var answers = new ArrayList<StorageReply.AdminResult>(1);
        boolean accepted;
        synchronized (watchers) {
            expired = expiredLocked(System.nanoTime());
            accepted = admissibleLocked(kind(command)) && (runHere(command, answers) || queueLocked(command, watcher));
        }
        timedOut(expired, System.nanoTime());
        answers.forEach(watcher);
        return accepted;
    }

    /** Whether one more command of this kind may run: below the watcher cap, and none of its kind still waiting. */
    private boolean admissibleLocked(String kind) {
        if (watchers.size() >= MAX_WATCHERS) {
            return false;
        }
        for (var watch : watchers) {
            if (watch.kind().equals(kind)) {
                return false;
            }
        }
        return true;
    }

    /** Runs a command that needs no writer and adds its answer. Any other command returns {@code false}. */
    private boolean runHere(AdminCommand command, List<StorageReply.AdminResult> answers) {
        String detail;
        if (command instanceof AdminCommand.Flush) {
            if (cache != null) {
                cache.requestFlush();
            }
            detail = "queued";
        } else if (command instanceof AdminCommand.CacheClear) {
            if (clearCache != null) {
                clearCache.run();
            }
            detail = "cleared";
        } else if (command instanceof AdminCommand.Tag tag) {
            if (tap != null) {
                tap.tag(tag.tag());
            }
            detail = tag.tag().isEmpty() ? "cleared" : "tagged";
        } else {
            return false;
        }
        answers.add(new StorageReply.AdminResult(nextId++, true, detail, System.nanoTime()));
        return true;
    }

    /**
     * Hands a command to the writer and watches for its answer. A memory reset drops the pending digests only once
     * the writer took it, so a refused reset loses nothing (JS-33).
     */
    private boolean queueLocked(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
        var id = nextId++;
        if (writer == null || !writer.request(new StorageWriter.Work.Command(command, id))) {
            return false;
        }
        if (command instanceof AdminCommand.MemoryReset reset && memoryReset != null) {
            memoryReset.accept(reset.subject());
        }
        watchers.add(new Watch(id, kind(command), watcher, System.nanoTime()));
        return true;
    }

    @Override
    public StorageHealth health() {
        if (writer == null || !aggregates) {
            return StorageHealth.DISABLED;
        }
        return published();
    }

    private StorageHealth published() {
        var health = writer.health();
        if (cache != null && cache.degraded() && health.state() == StorageState.READY) {
            return new StorageHealth(StorageState.DEGRADED, health.cause(), health.droppedEvents(), cache.inFlight(),
                    health.failedBatches());
        }
        return health;
    }

    private List<Watch> expiredLocked(long nanos) {
        var expired = new ArrayList<Watch>();
        var iterator = watchers.iterator();
        while (iterator.hasNext()) {
            var watch = iterator.next();
            if (nanos - watch.at() > WATCHER_NANOS) {
                expired.add(watch);
                iterator.remove();
            }
        }
        return expired;
    }

    private static void timedOut(List<Watch> expired, long nanos) {
        for (var watch : expired) {
            watch.watcher().accept(new StorageReply.AdminResult(watch.id(), false, "timed out", nanos));
        }
    }

    @Override
    public boolean submit(AggregateBatch batch) {
        return writer != null && aggregates && writer.submit(batch);
    }

    @Override
    public boolean request(AggregateQuery query) {
        return writer != null && aggregates && writer.request(new StorageWriter.Work.Query(query));
    }

    /**
     * Runs on the server thread before a memory reset is queued, so pending digests are dropped first.
     *
     * @param consumer the subject text
     */
    public void onMemoryReset(java.util.function.Consumer<String> consumer) {
        memoryReset = consumer;
    }

    private static String kind(AdminCommand command) {
        return switch (command) {
            case AdminCommand.Export export -> "export:" + export.kind();
            case AdminCommand.Prune ignored -> "prune";
            case AdminCommand.Verify ignored -> "verify";
            case AdminCommand.Backup ignored -> "backup";
            case AdminCommand.Flush ignored -> "flush";
            case AdminCommand.CacheClear ignored -> "cache";
            case AdminCommand.Tag ignored -> "tag";
            case AdminCommand.EvalReport report -> "eval:" + report.format();
            case AdminCommand.VerdictImport ignored -> "verdict";
            case AdminCommand.MemoryStats ignored -> "memory:stats";
            case AdminCommand.MemoryShow ignored -> "memory:show";
            case AdminCommand.MemoryReset ignored -> "memory:reset";
            case AdminCommand.MemoryPurge ignored -> "memory:purge";
            case AdminCommand.MemoryExport ignored -> "memory:export";
            case AdminCommand.MemoryImport ignored -> "memory:import";
            case AdminCommand.LearnBuild ignored -> "learn:build";
            case AdminCommand.LearnExport ignored -> "learn:export";
            case AdminCommand.LearnImport ignored -> "learn:import";
            case AdminCommand.LearnRollback ignored -> "learn:rollback";
            case AdminCommand.LearnDisable ignored -> "learn:disable";
            case AdminCommand.DistillModeSet ignored -> "distill:mode";
            case AdminCommand.DistillDisable ignored -> "distill:disable";
            case AdminCommand.DistillBuild ignored -> "distill:build";
            case AdminCommand.DistillExport ignored -> "distill:export";
            case AdminCommand.DistillImport ignored -> "distill:import";
            case AdminCommand.DistillRollback ignored -> "distill:rollback";
            case AdminCommand.AutoconfigImport ignored -> "autoconfig:import";
            case AdminCommand.AutoconfigApply ignored -> "autoconfig:apply";
            case AdminCommand.AutoconfigShow ignored -> "autoconfig:show";
            case AdminCommand.AutoconfigRevert ignored -> "autoconfig:revert";
        };
    }

    private record Watch(long id, String kind, Consumer<StorageReply.AdminResult> watcher, long at) {
    }
}
