package akyrion.thepredict.engine;

import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.learn.ValueStatDelta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Server-thread snapshot of {@code outcome_stats} (M21.6). Each delta lives in exactly one place: the pending map,
 * the ring of eight batches, or the snapshot. A full ring does not build another batch. {@code WARMING} lasts until
 * the first {@link StorageReply.OutcomeStatsLoaded}.
 */
public final class AggregateCache implements StorageTick {
    /** Batches that may be in flight. */
    public static final int RING_CAPACITY = 8;
    /** Most keys in the snapshot. */
    public static final int SNAPSHOT_CAP = 50_000;
    /** Most outcome keys in one ordinary batch. */
    public static final int FLUSH_KEYS = 1024;
    /** Ring full for this long becomes {@link StorageState#DEGRADED}. */
    public static final long DEGRADED_AFTER_NANOS = 60_000_000_000L;

    private final AggregateStore store;
    private final StorageMetrics metrics;
    private final int maxPending;
    private final int flushInterval;
    private final int repliesPerTick;
    private final Inbox<InboxEntry> inbox;
    private final LinkedHashMap<OutcomeStatKey, OutcomeStat> pending = new LinkedHashMap<>();
    private final LinkedHashMap<String, DecisionDailyDelta> decisions = new LinkedHashMap<>();
    private final LinkedHashMap<String, AnswerCacheDelta> cacheRows = new LinkedHashMap<>();
    private final LinkedHashMap<String, CalibrationBinDelta> calibration = new LinkedHashMap<>();
    private final ArrayDeque<AggregateBatch> ring = new ArrayDeque<>();
    private final LinkedHashMap<OutcomeStatKey, OutcomeStat> snapshot = new LinkedHashMap<>();
    private long nextBatchId = 1;
    private boolean warming = true;
    private boolean degraded;
    private long ringFullSince = Long.MIN_VALUE;
    private boolean forceFlush;
    private long epochMs;
    private Consumer<StorageReply.AdminResult> admin = ignored -> {
    };
    private Consumer<StorageReply.DifficultyStatsLoaded> difficultyStats = ignored -> {
    };
    private Consumer<StorageReply.VerdictsLoaded> verdicts = ignored -> {
    };
    private Consumer<StorageReply.AgreementStatsPage> agreements = ignored -> {
    };
    private Consumer<StorageReply.SubjectMemoryLoaded> subjectMemory = ignored -> {
    };
    private Consumer<StorageReply.MemoryApplied> memoryApplied = ignored -> {
    };
    private java.util.function.Consumer<AggregateBatch> memoryFailed = ignored -> {
    };
    private GateVerdicts gates;
    private int verdictLimit;
    private final ArrayList<DifficultyStatDelta> difficultyRows = new ArrayList<>();
    private final ArrayList<PatternStatDelta> patternRows = new ArrayList<>();
    private final java.util.ArrayList<PlayerMemoryDelta> memoryRows = new java.util.ArrayList<>();
    private final java.util.ArrayList<ValueStatDelta> valueRows = new java.util.ArrayList<>();
    private final java.util.ArrayList<akyrion.thepredict.domain.distill.ChoiceStatDelta> choiceRows =
            new java.util.ArrayList<>();
    private final java.util.ArrayList<akyrion.thepredict.domain.distill.AgreementStatDelta> agreementRows =
            new java.util.ArrayList<>();

    /**
     * Creates the cache.
     *
     * @param store where batches go
     * @param metrics counters
     * @param maxPendingKeys pending key cap
     * @param flushIntervalTicks ticks between flushes
     * @param repliesPerTick replies applied per tick
     * @param inbox storage lane
     */
    public AggregateCache(AggregateStore store, StorageMetrics metrics, int maxPendingKeys, int flushIntervalTicks,
                          int repliesPerTick, Inbox<InboxEntry> inbox) {
        this.store = store;
        this.metrics = metrics;
        this.maxPending = maxPendingKeys;
        this.flushInterval = flushIntervalTicks;
        this.repliesPerTick = repliesPerTick;
        this.inbox = inbox;
    }

    /**
     * Receives a loaded subject row.
     *
     * @param consumer the listener
     */
    public void onSubjectMemory(Consumer<StorageReply.SubjectMemoryLoaded> consumer) {
        subjectMemory = consumer;
    }

    /**
     * Receives a batch that replaced memory rows.
     *
     * @param consumer the listener
     */
    public void onMemoryApplied(Consumer<StorageReply.MemoryApplied> consumer) {
        memoryApplied = consumer;
    }

    /**
     * Queues memory rows for the next flush.
     *
     * @param rows the rows
     */
    public void acceptMemory(java.util.List<PlayerMemoryDelta> rows) {
        accept(memoryRows, rows);
    }

    /**
     * Drops memory rows that have not been flushed.
     *
     * @param subject the player
     */
    public void dropMemory(java.util.UUID subject) {
        memoryRows.removeIf(row -> row.subject().equals(subject));
    }

    /**
     * Queues a read on the storage thread.
     *
     * @param query the read
     * @return {@code false} when the queue is full
     */
    public boolean request(AggregateQuery query) {
        return store.request(query);
    }

    /**
     * Hears a failed batch that carried memory.
     *
     * @param consumer the listener
     */
    public void onMemoryFailed(java.util.function.Consumer<AggregateBatch> consumer) {
        memoryFailed = consumer;
    }

    /**
     * A cache over {@link AggregateStore#NONE} that never fills the ring.
     *
     * @param metrics counters
     * @return the cache
     */
    public static AggregateCache off(StorageMetrics metrics) {
        return new AggregateCache(AggregateStore.NONE, metrics, 4096, 100, 4, new Inbox<>(8));
    }

    /**
     * Folds one closed window into the pending map. O(1).
     *
     * @param profile profile id
     * @param summary the window
     * @param epochMs wall time of the close
     */
    public void outcomeClosed(String profile, OutcomeWindow.Summary summary, long epochMs) {
        this.epochMs = epochMs;
        var key = new OutcomeStatKey(profile, summary.tactic(), summary.movement(), summary.level(),
                OutcomeStatKey.context(summary.startBand().map(Enum::name).orElse(""),
                        summary.targetKind().map(Enum::name).orElse("")));
        var one = OutcomeStatMath.add(OutcomeStat.EMPTY, summary.effect(), summary.dealt(), summary.taken(),
                summary.reached(), summary.mobDied(), summary.targetDied(), summary.casts(), summary.hits());
        var existing = pending.get(key);
        if (existing == null && pending.size() >= maxPending) {
            metrics.keyDropped();
            return;
        }
        pending.put(key, existing == null ? one : OutcomeStatMath.merge(existing, one));
    }

    /**
     * Adds a daily decision row, merged by its key.
     *
     * @param delta the row
     */
    public void addDecision(DecisionDailyDelta delta) {
        var id = delta.day() + "|" + delta.model() + "|" + delta.profile() + "|" + delta.questionDetail();
        var existing = decisions.get(id);
        decisions.put(id, existing == null ? delta : new DecisionDailyDelta(delta.day(), delta.model(), delta.profile(),
                delta.questionDetail(), existing.requests() + delta.requests(), existing.answered() + delta.answered(),
                existing.failed() + delta.failed(), existing.inputTokens() + delta.inputTokens(),
                existing.answeredCache() + delta.answeredCache(),
                existing.inputTokensSaved() + delta.inputTokensSaved(), existing.adopted() + delta.adopted(),
                existing.discarded() + delta.discarded()));
    }

    /**
     * Adds a daily cache row, merged by its key.
     *
     * @param delta the row
     */
    public void addCache(AnswerCacheDelta delta) {
        var id = delta.day() + "|" + delta.model() + "|" + delta.mode();
        var existing = cacheRows.get(id);
        if (existing == null) {
            cacheRows.put(id, delta);
            return;
        }
        var p95 = Double.isNaN(delta.confidenceDeltaP95()) ? existing.confidenceDeltaP95() : delta.confidenceDeltaP95();
        cacheRows.put(id, new AnswerCacheDelta(delta.day(), delta.model(), delta.mode(),
                existing.lookups() + delta.lookups(), existing.hits() + delta.hits(),
                existing.compared() + delta.compared(), existing.agreeBest() + delta.agreeBest(), p95));
    }

    /**
     * Adds one calibration bin. At most 20 bins are kept per model, detail and target (M22.6).
     *
     * @param delta the bin
     */
    public void noteBin(CalibrationBinDelta delta) {
        var group = delta.model() + "|" + delta.questionDetail() + "|" + delta.target();
        var id = group + "|" + delta.bin();
        var existing = calibration.get(id);
        if (existing == null) {
            var count = 0;
            for (var key : calibration.keySet()) {
                if (key.startsWith(group + "|")) {
                    count++;
                }
            }
            if (count >= CalibrationBins.COUNT) {
                return;
            }
            calibration.put(id, delta);
            return;
        }
        calibration.put(id, new CalibrationBinDelta(delta.model(), delta.questionDetail(), delta.target(), delta.bin(),
                existing.n() + delta.n(), existing.confidenceSum() + delta.confidenceSum(),
                existing.successSum() + delta.successSum(), Math.max(existing.lastEpochMs(), delta.lastEpochMs())));
    }

    /**
     * Receives admin results drained from the storage lane. The server thread calls the listener.
     *
     * @param listener the listener
     */
    public void hearAdmin(Consumer<StorageReply.AdminResult> listener) {
        admin = listener;
    }

    /**
     * Receives difficulty rows drained from the storage lane.
     *
     * @param listener the listener
     */
    public void hearDifficulty(Consumer<StorageReply.DifficultyStatsLoaded> listener) {
        difficultyStats = listener;
    }

    /**
     * Receives gate verdicts drained from the storage lane.
     *
     * @param listener the listener
     */
    public void hearVerdicts(Consumer<StorageReply.VerdictsLoaded> listener) {
        verdicts = listener;
    }

    /**
     * Receives agreement pages drained between ticks. The listener publishes the coverage index.
     *
     * @param listener the listener
     */
    public void hearAgreements(Consumer<StorageReply.AgreementStatsPage> listener) {
        agreements = listener;
    }

    /** Asks the next tick to flush even off the interval. */
    public void requestFlush() {
        forceFlush = true;
    }

    /**
     * Whether the first load has not arrived.
     *
     * @return {@code true} while warming
     */
    public boolean warming() {
        return warming;
    }

    /**
     * Whether the ring has been full for 60 s.
     *
     * @return {@code true} when degraded
     */
    public boolean degraded() {
        return degraded;
    }

    /**
     * Batches in the ring.
     *
     * @return the count
     */
    public int inFlight() {
        return ring.size();
    }

    /**
     * Batches still waiting for a reply, oldest first.
     *
     * @return a copy of the ring
     */
    public List<AggregateBatch> batches() {
        return List.copyOf(ring);
    }

    /**
     * Reads one key: snapshot, then the ring, then pending.
     *
     * @param key the key
     * @return the merged stat
     */
    public Optional<OutcomeStat> outcome(OutcomeStatKey key) {
        OutcomeStat value = snapshot.get(key);
        for (var batch : ring) {
            for (var delta : batch.outcomes()) {
                if (delta.key().equals(key)) {
                    value = value == null ? delta.stat() : OutcomeStatMath.merge(value, delta.stat());
                }
            }
        }
        var waiting = pending.get(key);
        if (waiting != null) {
            value = value == null ? waiting : OutcomeStatMath.merge(value, waiting);
        }
        return Optional.ofNullable(value);
    }

    @Override
    public void onTick(long tick, long nanos) {
        drain(repliesPerTick);
        if (store.health().state() == StorageState.DISABLED) {
            dropAll();
            return;
        }
        if (gates != null && gates.due(tick)) {
            request(new AggregateQuery.VerdictQuery(verdictLimit));
        }
        if (forceFlush || flushInterval > 0 && tick % flushInterval == 0) {
            forceFlush = false;
            flush(nanos, FLUSH_KEYS);
        }
    }

    /**
     * Asks the store for the gate verdicts every 60 s until a load arrives, so a verdict file written after the
     * start still reaches the engine (M22.5, JS-06). The plugin wires its {@code GateVerdicts} here.
     *
     * @param verdicts the in-memory gate status whose {@link GateVerdicts#due} paces the requests
     * @param limit rows per request
     */
    public void useVerdicts(GateVerdicts verdicts, int limit) {
        gates = java.util.Objects.requireNonNull(verdicts, "verdicts");
        verdictLimit = limit;
    }

    /**
     * Asks the store for the difficulty rows of a profile; the reply reaches {@link #hearDifficulty}.
     *
     * @param profile profile id
     * @return whether the store accepted the query
     */
    public boolean requestDifficulty(String profile) {
        return request(new AggregateQuery.DifficultyStatsQuery(profile));
    }

    /**
     * Builds one batch of at most {@code maxKeys} outcome keys. A full ring builds nothing.
     *
     * @param nanos monotonic time
     * @param maxKeys key cap; the shutdown batch passes the whole pending map
     * @return the batch handed to the store, empty when nothing was sent
     */
    public Optional<AggregateBatch> flush(long nanos, int maxKeys) {
        if (store.health().state() == StorageState.DISABLED) {
            dropAll();
            return Optional.empty();
        }
        if (ring.size() >= RING_CAPACITY) {
            metrics.ringFull();
            if (ringFullSince == Long.MIN_VALUE) {
                ringFullSince = nanos;
            }
            if (nanos - ringFullSince >= DEGRADED_AFTER_NANOS) {
                degraded = true;
            }
            return Optional.empty();
        }
        ringFullSince = Long.MIN_VALUE;
        if (isEmpty()) {
            return Optional.empty();
        }
        var taken = new ArrayList<OutcomeStatDelta>();
        var keys = new ArrayList<OutcomeStatKey>();
        for (var entry : pending.entrySet()) {
            if (taken.size() >= maxKeys) {
                break;
            }
            keys.add(entry.getKey());
            taken.add(new OutcomeStatDelta(entry.getKey(), entry.getValue(), epochMs));
        }
        var batch = batch(taken);
        if (!store.submit(batch)) {
            return Optional.empty();
        }
        keys.forEach(pending::remove);
        dropRows();
        ring.addLast(batch);
        nextBatchId++;
        return Optional.of(batch);
    }

    /**
     * The final batch, without the 1 024 cap. The caller delivers it directly, not through {@link #flush}.
     *
     * @return the batch, empty when nothing is pending
     */
    public Optional<AggregateBatch> finalBatch() {
        if (isEmpty()) {
            return Optional.empty();
        }
        var taken = new ArrayList<OutcomeStatDelta>();
        pending.forEach((key, stat) -> taken.add(new OutcomeStatDelta(key, stat, epochMs)));
        var batch = batch(taken);
        dropAll();
        nextBatchId++;
        return Optional.of(batch);
    }

    /** One batch with the outcome deltas taken and every row list pending now. */
    private AggregateBatch batch(List<OutcomeStatDelta> taken) {
        return new AggregateBatch(nextBatchId, taken, List.copyOf(decisions.values()), List.copyOf(cacheRows.values()),
                List.copyOf(difficultyRows), List.copyOf(calibration.values()), List.copyOf(patternRows),
                List.copyOf(memoryRows), List.copyOf(valueRows), List.copyOf(choiceRows), List.copyOf(agreementRows));
    }

    /** Whether nothing waits for a batch. Every collection a batch carries is checked (rule A5). */
    private boolean isEmpty() {
        return pending.isEmpty() && decisions.isEmpty() && cacheRows.isEmpty() && calibration.isEmpty()
                && memoryRows.isEmpty() && valueRows.isEmpty() && choiceRows.isEmpty() && agreementRows.isEmpty()
                && difficultyRows.isEmpty() && patternRows.isEmpty();
    }

    /** Drops the pending outcome keys and every row list, as a disabled store or the final batch requires. */
    private void dropAll() {
        pending.clear();
        dropRows();
    }

    /** Drops every row list after a batch took them; the outcome keys are removed by the caller. */
    private void dropRows() {
        decisions.clear();
        cacheRows.clear();
        calibration.clear();
        memoryRows.clear();
        valueRows.clear();
        choiceRows.clear();
        agreementRows.clear();
        difficultyRows.clear();
        patternRows.clear();
    }

    /**
     * Appends rows to a list under the pending cap (rule A5). Rows past the cap are dropped and counted, like a
     * pending key that does not fit; a full ring keeps the lists from draining, so the cap bounds them meanwhile.
     */
    private <T> void accept(ArrayList<T> into, List<T> rows) {
        for (var row : rows) {
            if (into.size() >= maxPending) {
                metrics.keyDropped();
                continue;
            }
            into.add(row);
        }
    }

    /**
     * Adds learned-value rows for the current tick. They leave on the next flush or the shutdown batch.
     *
     * @param rows rows for {@code m28_value_stats}
     */
    public void acceptValues(List<ValueStatDelta> rows) {
        accept(valueRows, rows);
    }

    /**
     * Adds teaching rows. They leave on the next flush or the shutdown batch.
     *
     * @param rows rows for {@code m29_choice_stats}
     */
    public void acceptChoices(List<akyrion.thepredict.domain.distill.ChoiceStatDelta> rows) {
        accept(choiceRows, rows);
    }

    /**
     * Adds agreement rows. They are metrics, never labels.
     *
     * @param rows rows for {@code m29_agreement_stats}
     */
    public void acceptAgreements(List<akyrion.thepredict.domain.distill.AgreementStatDelta> rows) {
        accept(agreementRows, rows);
    }

    /**
     * Adds difficulty rows, one per fight clock closed (M22). They leave on the next flush or the shutdown batch.
     *
     * @param rows rows for {@code m22_difficulty_stats}
     */
    public void acceptDifficulty(List<DifficultyStatDelta> rows) {
        accept(difficultyRows, rows);
    }

    /**
     * Adds pattern rows from the summary of an encounter (M26). They leave on the next flush or the shutdown batch.
     *
     * @param rows rows for {@code m26_pattern_stats}
     */
    public void acceptPatterns(List<PatternStatDelta> rows) {
        accept(patternRows, rows);
    }

    /**
     * Applies at most {@code maximum} storage replies.
     *
     * @param maximum the cap
     * @return replies applied
     */
    public int drain(int maximum) {
        var applied = inbox.drainStorage(maximum, this::apply);
        metrics.replies(applied);
        return applied;
    }

    private void apply(StorageReply reply) {
        switch (reply) {
            case StorageReply.BatchApplied applied -> applied(applied.batchId());
            case StorageReply.BatchFailed failed -> failed(failed.batchId());
            case StorageReply.OutcomeStatsLoaded loaded -> loaded(loaded);
            case StorageReply.QueryFailed ignored -> {
            }
            case StorageReply.AdminResult result -> admin.accept(result);
            case StorageReply.DifficultyStatsLoaded loaded -> difficultyStats.accept(loaded);
            case StorageReply.VerdictsLoaded loaded -> verdicts.accept(loaded);
            case StorageReply.SubjectMemoryLoaded loaded -> subjectMemory.accept(loaded);
            case StorageReply.MemoryApplied applied -> {
                applied(applied.batchId());
                memoryApplied.accept(applied);
            }
            case StorageReply.ValueStatsPage ignored -> {
            }
            case StorageReply.ArtifactsLoaded ignored -> {
            }
            case StorageReply.ChoiceStatsPage ignored -> {
            }
            case StorageReply.AgreementStatsPage page -> agreements.accept(page);
        }
    }

    private void applied(long batchId) {
        AggregateBatch found = null;
        for (var batch : ring) {
            if (batch.batchId() == batchId) {
                found = batch;
                break;
            }
        }
        if (found == null) {
            metrics.replyUnknown();
            return;
        }
        ring.remove(found);
        noteRing();
        for (var delta : found.outcomes()) {
            var existing = snapshot.get(delta.key());
            if (existing == null && snapshot.size() >= SNAPSHOT_CAP) {
                metrics.snapshotKeyDropped();
                continue;
            }
            snapshot.put(delta.key(), existing == null ? delta.stat() : OutcomeStatMath.merge(existing, delta.stat()));
        }
    }

    private void failed(long batchId) {
        AggregateBatch found = null;
        var iterator = ring.iterator();
        while (iterator.hasNext()) {
            var batch = iterator.next();
            if (batch.batchId() == batchId) {
                found = batch;
                iterator.remove();
            }
        }
        if (found == null) {
            metrics.replyUnknown();
            return;
        }
        if (!found.memory().isEmpty()) {
            memoryFailed.accept(found);
        }
        noteRing();
        metrics.batchFailed();
    }

    private void noteRing() {
        if (ring.size() < RING_CAPACITY) {
            ringFullSince = Long.MIN_VALUE;
            degraded = false;
        }
    }

    private void loaded(StorageReply.OutcomeStatsLoaded loaded) {
        snapshot.clear();
        for (var row : loaded.rows()) {
            if (snapshot.size() >= SNAPSHOT_CAP) {
                break;
            }
            snapshot.put(row.key(), row.stat());
        }
        ring.removeIf(batch -> batch.batchId() <= loaded.appliedThroughBatch());
        noteRing();
        warming = false;
    }
}
