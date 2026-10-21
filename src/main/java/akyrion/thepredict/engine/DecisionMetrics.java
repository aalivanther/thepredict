package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.PruneReason;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/**
 * Latency per span and counters per cause of the decision path (M09.6, rules A5, A7). Server thread only; times
 * measured on HTTP threads travel inside {@link InboxEntry.CompletedRequest}. Memory is fixed: one
 * {@link LatencyWindow} per span, one counter per value of each counted enum and the last
 * {@link #RECENT_CAUSES} failures and fallbacks with their tick for {@code /thepredict status} (M12.1), never a
 * series per UUID (plan §12).
 */
public final class DecisionMetrics {
    /** Most recent {@link FailureKind} and {@link FallbackReason} values kept, each with its tick. */
    public static final int RECENT_CAUSES = 3;

    /** Measured spans of a decision. */
    public enum Span {
        /** Duration of {@code CombatReader.capture}. */
        CAPTURE,
        /** Duration of {@code DecisionClient.prepare}: encoding and deduplication on the server thread (M16.1). */
        PREPARE,
        /** Duration of the call to {@code DecisionClient.decide}. */
        DISPATCH,
        /** From the send to the completion on the HTTP thread: encoding, network, model and parsing. */
        ROUNDTRIP,
        /** From the completion to the drain of the inbox. */
        INBOX_WAIT,
        /** Acceptance and publication of the intents (M10). */
        APPLY,
        /** Total work of one coordinator tick. */
        TICK_WORK
    }

    /** Plain counters. */
    public enum Counter {
        /** Answers received. */
        ANSWERED,
        /** Input tokens reported by the answers. */
        INPUT_TOKENS,
        /** Requests released by the watchdog after their deadline. */
        WATCHDOG_RELEASES,
        /** Entries the inbox rejected because it was full. */
        INBOX_REJECTIONS,
        /** Ticks whose steps 5 and 6 waited for the next tick because of the work budget. */
        DEFERRED_TICKS,
        /** Steps that failed with an exception and were contained. */
        STEP_FAILURES,
        /** Sessions that fell to level 4 because processing them threw. */
        SESSION_FAILURES,
        /** Options of the planned questions that read the same as another and were not sent (M16.1). */
        OPTIONS_MERGED,
        /** Contingency questions sent (M16.6). */
        CONTINGENCIES_ASKED,
        /** Contingencies that fired and became the intent (M10.4, M16.6). */
        CONTINGENCIES_FIRED,
        /** Fired contingencies whose outcome window closed with a positive effect (M16.6, M16.8). */
        CONTINGENCIES_EFFECTIVE
    }

    private final Map<Span, LatencyWindow> spans = new EnumMap<>(Span.class);
    private final Map<Counter, long[]> counters = new EnumMap<>(Counter.class);
    private final Map<Class<?>, long[]> causes = Map.of(
            FailureKind.class, new long[FailureKind.values().length],
            DecisionDispatcher.Outcome.class, new long[DecisionDispatcher.Outcome.values().length],
            FallbackReason.class, new long[FallbackReason.values().length],
            ExecutionOutcome.class, new long[ExecutionOutcome.values().length],
            DiscardReason.class, new long[DiscardReason.values().length]);
    private final Map<Class<?>, Deque<Recent>> recent = Map.of(FailureKind.class, new ArrayDeque<>(RECENT_CAUSES),
            FallbackReason.class, new ArrayDeque<>(RECENT_CAUSES));
    private final LongSupplier currentTick;
    private final long[] ready = new long[ReadyCounter.values().length];
    private final long[] layer = new long[LayerCounter.values().length];
    private final long[] cache = new long[CacheCounter.values().length];
    private final long[] pruned = new long[PruneReason.values().length];
    private final long[] readyLatency = new long[4];
    private final long[] zone = new long[ZoneCounter.values().length];
    private final long[] group = new long[GroupCounter.values().length];
    private final long[] nativeAlerts = new long[4];
    private final long[] tell = new long[TellCounter.values().length];
    private final long[] pattern = new long[PatternCounter.values().length];
    private long tellWindowStart = Long.MIN_VALUE;
    private long windowOpened;
    private long windowAborted;

    /**
     * Tell and stance counters. Parallel to {@link Counter} so published goldens keep their lines (M23).
     * Lead buckets are 0, 1–5, 6–10, 11–20 and above 20.
     */
    public enum TellCounter {
        /** Tells opened. */
        TELEGRAPHS,
        /** Opens whose lead is 0. */
        TELEGRAPH_LEAD_0,
        /** Opens whose lead is 1 to 5. */
        TELEGRAPH_LEAD_1_5,
        /** Opens whose lead is 6 to 10. */
        TELEGRAPH_LEAD_6_10,
        /** Opens whose lead is 11 to 20. */
        TELEGRAPH_LEAD_11_20,
        /** Opens whose lead is above 20. */
        TELEGRAPH_LEAD_OVER_20,
        /** Tells that closed. */
        TELEGRAPH_END,
        /** Closes that were {@code REJECTED} or {@code SESSION_INVALID}. */
        TELEGRAPH_SELF_ABORT,
        /** Releases deferred because the target was still invulnerable. */
        RELEASE_DEFERRED,
        /** Releases rejected because the skill was on cooldown. */
        WHIFF_COOLDOWN_BLOCKED,
        /** Hits whose minimum was skipped by an opt-out. */
        UNWARNED_BY_OPT_OUT,
        /** Times the author stance won over write mode. */
        NATIVE_STANCE_CONFLICT
    }

    /**
     * Group counters. Parallel to {@link Counter} so published goldens keep their lines (M25).
     */
    public enum GroupCounter {
        /** Replans deferred by the per-world cap or the budget. */
        GROUP_REPLAN_DEFERRED,
        /** Reads of a stale epoch. */
        GROUP_STALE_EPOCH,
        /** Ticks a copy was stale and the session fell back to fair. */
        GROUP_COPY_STALE_TICKS,
        /** Leader reserves that expired without a tell. */
        LEADER_RESERVE_EXPIRED,
        /** Ticks a real impact differed from its reservation. */
        IMPACT_DEVIATION_TICKS,
        /** Tokens granted. */
        TOKEN_GRANTED,
        /** Tokens stolen. */
        TOKEN_STOLEN,
        /** Tokens released because they were idle. */
        TOKEN_IDLE_TIMEOUT,
        /** Tokens yielded. No producer in this module. */
        TOKEN_YIELDED,
        /** Fairness waits, one bucket for every reason. */
        GROUP_FAIRNESS_DEFERRED,
        /** Vanilla alerts that were counted and not cancelled. */
        NATIVE_GROUP_ALERTS,
        /** Assignments a shared threat table would not take. */
        ASSIGNMENT_UNAVAILABLE,
        /** Encounters of one side that stayed separate. */
        GROUP_FRAGMENTED,
        /** Bodies that differed only by the group block. */
        BODY_CHURN_BY_GROUP_RATIO
    }

    /**
     * Pattern counters. Parallel to {@link Counter} so published goldens keep their lines (M26).
     */
    public enum PatternCounter {
        /** Kinematic reads deferred by the per-tick cap. */
        PATTERN_READS_DEFERRED,
        /** Movement tokens the bucket dropped. */
        PATTERN_TOKENS_DROPPED,
        /** Pattern steps that waited for the next tick. */
        PATTERN_STEP_DEFERRED,
        /** Active patterns whose profile had no fitting choice. */
        PATTERN_NO_COUNTER
    }

    /** Zone counters. Parallel to {@link Counter} so published goldens keep their lines (M24). */
    public enum ZoneCounter {
        /** Queries answered. */
        ZONE_QUERIES,
        /** Queries deferred by the per-tick cap. */
        ZONE_QUERIES_DEFERRED,
        /** Builds published. */
        ZONE_BUILDS,
        /** Builds discarded because the epoch was stale. */
        ZONE_BUILD_STALE,
        /** Columns read on the server thread. */
        ZONE_COLUMNS,
        /** Dirty keys dropped because the queue was full. */
        ZONE_DIRTY_DROPPED,
        /** Casts deferred by the push policy. */
        ZONE_PUSH_DEFERRED,
        /** Columns a verification corrected without an event. */
        ZONE_UNSEEN_CHANGES
    }

    /** Counters of the ready cast and the pruning that stay out of {@link Counter} so published goldens keep their lines. */
    public enum ReadyCounter {
        /** Sessions whose ready tick was due and were checked. */
        READY_CHECKS,
        /** Casts that started from the ready step. */
        READY_CASTS,
        /** Due sessions left for the next tick by the per-tick cap. */
        READY_DEFERRED,
        /** Casts refused because a wind-up was still running. */
        BUSY_BLOCKED,
        /** Safe veto passes undone because fewer than two options would have remained. */
        PRUNE_UNDONE,
        /** Questions whose option list changed because pruning was on. */
        RESOLVED_BY_PRUNING
    }

    /** Counters of the tick layer. Parallel to {@link ReadyCounter} so published goldens keep their lines (M20). */
    public enum LayerCounter {
        /** Sessions the visit cap left for the next tick. */
        TICK_LAYER_DEFERRED,
        /** Ticks the layer ran a degraded step. */
        TICK_LAYER_DEGRADED,
        /** Micro steps that found no path. */
        MICRO_NO_PATH,
        /** Micro steps still waiting for a path. */
        MICRO_PATH_PENDING,
        /** Waypoint budget used up. */
        WAYPOINT_BUDGET_OVERRUN,
        /** Reactive intents whose deadline moved. */
        INTENT_EXTENDED,
        /** Answers dropped because they aged out of the horizon. */
        ANSWER_AGED
    }

    /**
     * Creates empty metrics.
     *
     * @param currentTick server tick, which dates the recent failures and fallbacks
     */
    public DecisionMetrics(LongSupplier currentTick) {
        this.currentTick = Objects.requireNonNull(currentTick, "currentTick");
        for (var span : Span.values()) {
            spans.put(span, new LatencyWindow());
        }
        for (var counter : Counter.values()) {
            counters.put(counter, new long[1]);
        }
    }

    /**
     * Adds to a ready-cast or pruning counter. These counters are not part of {@link Counter}, so a golden that lists
     * that enum stays byte-identical (M19).
     *
     * @param counter which counter
     * @param amount how much to add
     */
    public void add(ReadyCounter counter, long amount) {
        ready[Objects.requireNonNull(counter, "counter").ordinal()] += amount;
    }

    /**
     * Reads a ready-cast or pruning counter.
     *
     * @param counter which counter
     * @return the total
     */
    public long total(ReadyCounter counter) {
        return ready[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    /**
     * Adds to a zone counter. These counters are not part of {@link Counter}.
     *
     * @param counter which counter
     * @param amount how much to add
     */
    public void add(ZoneCounter counter, long amount) {
        zone[Objects.requireNonNull(counter, "counter").ordinal()] += amount;
    }

    /**
     * Reads a zone counter.
     *
     * @param counter which counter
     * @return the total
     */
    public long total(ZoneCounter counter) {
        return zone[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    /**
     * Adds to a group counter. These counters are not part of {@link Counter}.
     *
     * @param counter which counter
     * @param amount how much to add
     */
    public void add(GroupCounter counter, long amount) {
        group[Objects.requireNonNull(counter, "counter").ordinal()] += amount;
    }

    /**
     * Counts one vanilla group alert and the bucket of its reason. The four reasons are fixed: nearby attack,
     * reinforcement, follow leader, owner attacked.
     *
     * @param reason index 0 to 3
     */
    public void noteNativeGroupAlert(int reason) {
        add(GroupCounter.NATIVE_GROUP_ALERTS, 1);
        if (reason >= 0 && reason < nativeAlerts.length) {
            nativeAlerts[reason]++;
        }
    }

    /**
     * Alerts counted for one reason.
     *
     * @param reason index 0 to 3
     * @return the count
     */
    public long nativeGroupAlert(int reason) {
        return reason < 0 || reason >= nativeAlerts.length ? 0 : nativeAlerts[reason];
    }

    /**
     * Adds to a pattern counter. These counters are not part of {@link Counter}.
     *
     * @param counter which counter
     * @param amount how much to add
     */
    public void add(PatternCounter counter, long amount) {
        pattern[Objects.requireNonNull(counter, "counter").ordinal()] += amount;
    }

    /**
     * Reads a pattern counter.
     *
     * @param counter which counter
     * @return the total
     */
    public long total(PatternCounter counter) {
        return pattern[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    /**
     * Reads a group counter.
     *
     * @param counter which counter
     * @return the total
     */
    public long total(GroupCounter counter) {
        return group[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    /**
     * Adds to a tell counter. These counters are not part of {@link Counter}.
     *
     * @param counter which counter
     * @param amount how much to add
     */
    public void add(TellCounter counter, long amount) {
        tell[Objects.requireNonNull(counter, "counter").ordinal()] += amount;
    }

    /**
     * Reads a tell counter.
     *
     * @param counter which counter
     * @return the total
     */
    public long total(TellCounter counter) {
        return tell[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    /**
     * Counts one opened tell and its lead bucket. The 10-minute self-abort window rolls here.
     *
     * @param tick server tick
     * @param lead ticks inserted before the cast
     */
    public void noteTellOpen(long tick, int lead) {
        rollTellWindow(tick);
        windowOpened++;
        add(TellCounter.TELEGRAPHS, 1);
        add(leadBucket(lead), 1);
    }

    /**
     * Counts one closed tell.
     *
     * @param tick server tick
     */
    public void noteTellEnd(long tick) {
        add(TellCounter.TELEGRAPH_END, 1);
    }

    /**
     * Counts one self-abort inside the 10-minute window and for the lifetime.
     *
     * @param tick server tick
     */
    public void noteSelfAbort(long tick) {
        rollTellWindow(tick);
        windowAborted++;
        add(TellCounter.TELEGRAPH_SELF_ABORT, 1);
    }

    /**
     * Tells opened in the current 10-minute window.
     *
     * @return the count
     */
    public long windowOpened() {
        return windowOpened;
    }

    /**
     * Self-aborts in the current 10-minute window.
     *
     * @return the count
     */
    public long windowAborted() {
        return windowAborted;
    }

    private void rollTellWindow(long tick) {
        if (tellWindowStart == Long.MIN_VALUE) {
            tellWindowStart = tick;
        }
        if (tick - tellWindowStart >= 12_000L) {
            tellWindowStart = tick;
            windowOpened = 0;
            windowAborted = 0;
        }
    }

    private static TellCounter leadBucket(int lead) {
        if (lead <= 0) {
            return TellCounter.TELEGRAPH_LEAD_0;
        }
        if (lead <= 5) {
            return TellCounter.TELEGRAPH_LEAD_1_5;
        }
        if (lead <= 10) {
            return TellCounter.TELEGRAPH_LEAD_6_10;
        }
        if (lead <= 20) {
            return TellCounter.TELEGRAPH_LEAD_11_20;
        }
        return TellCounter.TELEGRAPH_LEAD_OVER_20;
    }

    /**
     * Adds to a tick-layer counter. These counters are not part of {@link Counter}.
     *
     * @param counter which counter
     * @param amount how much to add
     */
    public void layer(LayerCounter counter, long amount) {
        layer[Objects.requireNonNull(counter, "counter").ordinal()] += amount;
    }

    /**
     * Reads a tick-layer counter.
     *
     * @param counter which counter
     * @return the total
     */
    public long layer(LayerCounter counter) {
        return layer[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    /** Counters of the answer cache. Parallel to {@link Counter} so published goldens keep their lines (M21.7). */
    public enum CacheCounter {
        /** Lookups while the cache is not off. */
        LOOKUPS,
        /** Exact hits. */
        HITS,
        /** Entries past the ttl. */
        EXPIRED,
        /** Bodies that matched and question keys that did not. */
        KEY_MISMATCH,
        /** Shadow comparisons that agreed on the best tactic. */
        AGREE,
        /** Shadow comparisons that disagreed. */
        DISAGREE,
        /** Shadow notes dropped because the pending map was full. */
        SHADOW_SKIPPED_FULL,
        /** Comparisons whose cached capture was at most 20 ticks old. */
        AGE_0_20,
        /** Comparisons whose cached capture was 21 to 60 ticks old. */
        AGE_21_60,
        /** Comparisons whose cached capture was 61 to 100 ticks old. */
        AGE_61_100
    }

    /**
     * Adds to a cache counter.
     *
     * @param counter which counter
     * @param amount how much to add
     */
    public void cache(CacheCounter counter, long amount) {
        cache[Objects.requireNonNull(counter, "counter").ordinal()] += amount;
    }

    /**
     * Reads a cache counter.
     *
     * @param counter which counter
     * @return the total
     */
    public long cache(CacheCounter counter) {
        return cache[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    /**
     * Counts options removed for one cause.
     *
     * @param reason why the option left
     * @param amount how many
     */
    public void pruned(PruneReason reason, long amount) {
        pruned[Objects.requireNonNull(reason, "reason").ordinal()] += amount;
    }

    /**
     * Options removed for one cause.
     *
     * @param reason why
     * @return the total
     */
    public long pruned(PruneReason reason) {
        return pruned[Objects.requireNonNull(reason, "reason").ordinal()];
    }

    /**
     * Records the ticks from the tick a choice was ready until the cast started. Buckets are 0, 1, 2 and 3 or more.
     *
     * @param ticks delay; a negative one counts as zero
     */
    public void readyLatency(int ticks) {
        var bucket = ticks <= 0 ? 0 : Math.min(ticks, 3);
        readyLatency[bucket]++;
    }

    /**
     * Casts whose ready-to-cast delay fell in a bucket.
     *
     * @param bucket {@code 0}, {@code 1}, {@code 2} or {@code 3} for three or more
     * @return the count
     */
    public long readyLatencyBucket(int bucket) {
        if (bucket < 0 || bucket > 3) {
            throw new IllegalArgumentException("bucket must be 0 to 3, was " + bucket);
        }
        return readyLatency[bucket];
    }

    /**
     * Records one duration.
     *
     * @param span measured span
     * @param elapsedNanos duration; a negative one counts as zero
     * @param nowNanos monotonic time now, which selects the minute
     */
    public void record(Span span, long elapsedNanos, long nowNanos) {
        spans.get(Objects.requireNonNull(span, "span")).record(elapsedNanos, nowNanos);
    }

    /**
     * Reads a percentile of the current and previous minute by cumulative count.
     *
     * @param span measured span
     * @param ratio percentile as a ratio, such as {@code 0.95}
     * @param nowNanos monotonic time now
     * @return upper bound of the bucket in microseconds ({@link Long#MAX_VALUE} above two seconds); empty without
     *         samples in the last two minutes
     */
    public OptionalLong percentileMicros(Span span, double ratio, long nowNanos) {
        return spans.get(Objects.requireNonNull(span, "span")).percentileMicros(ratio, nowNanos);
    }

    /**
     * Counts every sample of a span since start.
     *
     * @param span measured span
     * @return samples
     */
    public long samples(Span span) {
        return spans.get(Objects.requireNonNull(span, "span")).samples();
    }

    /**
     * Returns the longest sample of a span since start.
     *
     * @param span measured span
     * @return microseconds
     */
    public long maxMicros(Span span) {
        return spans.get(Objects.requireNonNull(span, "span")).maxMicros();
    }

    /**
     * Adds to a plain counter.
     *
     * @param counter counter
     * @param amount amount, {@code >= 0}
     */
    public void add(Counter counter, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amount);
        }
        counters.get(Objects.requireNonNull(counter, "counter"))[0] += amount;
    }

    /**
     * Reads a plain counter.
     *
     * @param counter counter
     * @return total since start
     */
    public long total(Counter counter) {
        return counters.get(Objects.requireNonNull(counter, "counter"))[0];
    }

    /**
     * Counts one cause: a {@link FailureKind}, a {@link DecisionDispatcher.Outcome}, or, for M10, a
     * {@link FallbackReason}, {@link ExecutionOutcome} or {@link DiscardReason}.
     *
     * @param cause counted value
     * @throws IllegalArgumentException if the enum is not one of the counted ones
     */
    public void increment(Enum<?> cause) {
        slots(cause)[cause.ordinal()]++;
        var latest = recent.get(cause.getDeclaringClass());
        if (latest != null) {
            if (latest.size() == RECENT_CAUSES) {
                latest.removeFirst();
            }
            latest.addLast(new Recent(cause, currentTick.getAsLong()));
        }
    }

    /**
     * Returns the last failures of requests, oldest first.
     *
     * @return at most {@link #RECENT_CAUSES} {@link FailureKind} values with their tick
     */
    public List<Recent> recentFailures() {
        return List.copyOf(recent.get(FailureKind.class));
    }

    /**
     * Returns the last drops of the fallback chain, oldest first.
     *
     * @return at most {@link #RECENT_CAUSES} {@link FallbackReason} values with their tick
     */
    public List<Recent> recentFallbacks() {
        return List.copyOf(recent.get(FallbackReason.class));
    }

    /**
     * Reads the counter of one cause.
     *
     * @param cause counted value
     * @return occurrences since start
     * @throws IllegalArgumentException if the enum is not one of the counted ones
     */
    public long count(Enum<?> cause) {
        return slots(cause)[cause.ordinal()];
    }

    private long[] slots(Enum<?> cause) {
        var slots = causes.get(Objects.requireNonNull(cause, "cause").getDeclaringClass());
        if (slots == null) {
            throw new IllegalArgumentException("not a counted cause: " + cause.getDeclaringClass().getSimpleName());
        }
        return slots;
    }

    /**
     * One counted cause and the tick it was counted at.
     *
     * @param cause a {@link FailureKind} or {@link FallbackReason}
     * @param tick server tick of the count
     */
    public record Recent(Enum<?> cause, long tick) {
        /**
         * Validates the cause.
         *
         * @throws NullPointerException if the cause is {@code null}
         */
        public Recent {
            Objects.requireNonNull(cause, "cause");
        }
    }
}
