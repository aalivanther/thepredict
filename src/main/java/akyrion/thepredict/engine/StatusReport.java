package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.SessionState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Read-only report of the engine for {@code /thepredict status} (01 §8, M12.1), built by {@link Diagnostics} on the
 * server thread and safe to hand to any thread (rule A4). It holds fixed counters only: no key, header, player name or
 * UUID (rules C8, I6), and its size does not grow with the number of mobs.
 *
 * @param service state of the Jev service
 * @param sessions sessions by level and state and their lifecycle counters
 * @param encounters open encounters and their counters
 * @param server budget of the whole server
 * @param world budget of the world of the player who asked, empty from the console
 * @param consumption input tokens of the last minute and their limit
 * @param latency latency per measured span
 * @param recentFailures last request failures, oldest first
 * @param recentFallbacks last drops of the fallback chain, oldest first
 * @param counters counters by cause, each group with its non-zero values only
 * @param context counters of the context events, the {@code predictordecide} signals and the captures
 * @param threat counters of the enriched threat
 * @param effectiveness outcome windows by control level and the use of the contingencies (M16.6, M16.8)
 * @param storage the Storage block, empty when storage is not wired (M21.9)
 * @param patterns the pattern mode, empty when the gate is not wired (M26.11)
 */
public record StatusReport(Service service, Sessions sessions, Encounters encounters, Budget server,
                           Optional<Budget> world, Consumption consumption, Map<DecisionMetrics.Span, Latency> latency,
                           List<RecentCause> recentFailures, List<RecentCause> recentFallbacks,
                           List<CounterGroup> counters, Context context, Threat threat, Effectiveness effectiveness,
                           Optional<StorageStatus> storage, Optional<Patterns> patterns) {
    /**
     * Copies the collections.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public StatusReport {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(encounters, "encounters");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(consumption, "consumption");
        latency = ordered(latency);
        recentFailures = List.copyOf(recentFailures);
        recentFallbacks = List.copyOf(recentFallbacks);
        counters = List.copyOf(counters);
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(threat, "threat");
        Objects.requireNonNull(effectiveness, "effectiveness");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(patterns, "patterns");
    }

    /**
     * A report from before the Storage block (rule W5). The block is absent.
     *
     * @param service state of the Jev service
     * @param sessions sessions by level and state
     * @param encounters open encounters
     * @param server budget of the whole server
     * @param world budget of one world
     * @param consumption input tokens
     * @param latency latency per span
     * @param recentFailures last request failures
     * @param recentFallbacks last drops of the fallback chain
     * @param counters counters by cause
     * @param context context counters
     * @param threat threat counters
     * @param effectiveness outcome windows
     */
    public StatusReport(Service service, Sessions sessions, Encounters encounters, Budget server, Optional<Budget> world,
                        Consumption consumption, Map<DecisionMetrics.Span, Latency> latency,
                        List<RecentCause> recentFailures, List<RecentCause> recentFallbacks, List<CounterGroup> counters,
                        Context context, Threat threat, Effectiveness effectiveness) {
        this(service, sessions, encounters, server, world, consumption, latency, recentFailures, recentFallbacks,
                counters, context, threat, effectiveness, Optional.empty(), Optional.empty());
    }

    /**
     * Returns this report with the budget of the world of the player who asked.
     *
     * @param budget budget of that world
     * @return a copy that differs only in {@link #world()}
     */
    public StatusReport withWorld(Budget budget) {
        return new StatusReport(service, sessions, encounters, server, Optional.of(budget), consumption, latency,
                recentFailures, recentFallbacks, counters, context, threat, effectiveness, storage, patterns);
    }

    /**
     * Returns this report with the Storage block.
     *
     * @param block the block
     * @return a copy that differs only in {@link #storage()}
     */
    public StatusReport withStorage(StorageStatus block) {
        return new StatusReport(service, sessions, encounters, server, world, consumption, latency, recentFailures,
                recentFallbacks, counters, context, threat, effectiveness, Optional.of(block), patterns);
    }

    /**
     * Returns this report with the pattern mode.
     *
     * @param block the mode and, when there is no verdict, the cause
     * @return a copy that differs only in {@link #patterns()}
     */
    public StatusReport withPatterns(Patterns block) {
        return new StatusReport(service, sessions, encounters, server, world, consumption, latency, recentFailures,
                recentFallbacks, counters, context, threat, effectiveness, storage, Optional.of(block));
    }

    /**
     * The pattern line of {@code status}.
     *
     * @param mode effective mode
     * @param cause empty, or {@code UNVERIFIED_PATTERN_MODE}
     */
    public record Patterns(String mode, String cause) {
        /**
         * Checks the text.
         */
        public Patterns {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(cause, "cause");
        }
    }

    /**
     * What the adopted intents achieved (M16.8) and how the contingencies were used (M16.6).
     *
     * @param levels outcome totals of each control level with at least one closed window
     * @param contingenciesAsked contingency questions sent
     * @param contingenciesFired contingencies that fired
     * @param contingenciesEffective fired contingencies whose window closed with a positive effect
     */
    public record Effectiveness(List<OutcomeMetrics.LevelOutcomes> levels, long contingenciesAsked,
                                long contingenciesFired, long contingenciesEffective) {
        /**
         * Copies the levels.
         *
         * @throws NullPointerException if the list is {@code null}
         */
        public Effectiveness {
            levels = List.copyOf(levels);
        }
    }

    static <K, V> Map<K, V> ordered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
    }

    /**
     * State of the Jev service.
     *
     * @param status state and enumerated cause of its last change
     * @param pauseRemainingMillis time left in the pause, only while {@code PAUSED}
     * @param trial whether the trial after a check is still open
     * @param configuredModel configured model as it may be shown ({@code <custom model>} otherwise)
     * @param lastModel model of the last answer, labelled; empty before the first answer
     */
    public record Service(ServiceCheck status, OptionalLong pauseRemainingMillis, boolean trial, String configuredModel,
                          Optional<String> lastModel) {
        /**
         * Validates the components.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Service {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(pauseRemainingMillis, "pauseRemainingMillis");
            Objects.requireNonNull(configuredModel, "configuredModel");
            Objects.requireNonNull(lastModel, "lastModel");
        }
    }

    /**
     * Sessions of bound mobs.
     *
     * @param byLevel active sessions per control level, every level present
     * @param byState sessions per lifecycle state, every state present
     * @param debugIntents active sessions held by a debug intent
     * @param opened sessions opened since start
     * @param ended sessions ended since start per cause, in the order of the causes
     * @param pendingReleases goal releases waiting for their tick
     * @param failedReleases goal releases that failed since start
     */
    public record Sessions(Map<ControlLevel, Integer> byLevel, Map<SessionState, Integer> byState, int debugIntents,
                           long opened, Map<String, Long> ended, int pendingReleases, long failedReleases) {
        /** Copies the maps, keeping their order. */
        public Sessions {
            byLevel = ordered(byLevel);
            byState = ordered(byState);
            ended = ordered(ended);
        }
    }

    /**
     * Open encounters.
     *
     * @param active open encounters
     * @param requestInFlight encounters with a request in flight
     * @param contextPending encounters with pending context
     * @param opened encounters opened since start
     * @param closed encounters closed since start
     * @param averageTicks average duration of a closed encounter
     */
    public record Encounters(int active, int requestInFlight, int contextPending, long opened, long closed,
                             long averageTicks) {
    }

    /**
     * Requests of the last 60 seconds and in flight, against their limits.
     *
     * @param requestsLastMinute requests sent in the window
     * @param maxRequestsPerMinute limit of the window
     * @param inFlight requests in flight
     * @param maxInFlight limit of requests in flight
     */
    public record Budget(long requestsLastMinute, int maxRequestsPerMinute, int inFlight, int maxInFlight) {
    }

    /**
     * Input tokens of the last 60 seconds, real where known and estimated otherwise.
     *
     * @param inputTokensLastMinute tokens in the window
     * @param maxInputTokensPerMinute limit of the window
     */
    public record Consumption(long inputTokensLastMinute, int maxInputTokensPerMinute) {
    }

    /**
     * Latency of one span over the current and previous minute, by bucket upper bound.
     *
     * @param p50Micros median, empty without samples in the last two minutes
     * @param p95Micros 95th percentile, empty without samples
     * @param p99Micros 99th percentile, empty without samples
     * @param samples samples since start
     * @param maxMicros longest sample since start
     */
    public record Latency(OptionalLong p50Micros, OptionalLong p95Micros, OptionalLong p99Micros, long samples,
                          long maxMicros) {
        /**
         * Validates the components.
         *
         * @throws NullPointerException if a percentile is {@code null}
         */
        public Latency {
            Objects.requireNonNull(p50Micros, "p50Micros");
            Objects.requireNonNull(p95Micros, "p95Micros");
            Objects.requireNonNull(p99Micros, "p99Micros");
        }
    }

    /**
     * One recent cause and how long ago it was counted.
     *
     * @param cause enum name
     * @param ageTicks ticks since it was counted
     */
    public record RecentCause(String cause, long ageTicks) {
        /**
         * Validates the cause.
         *
         * @throws NullPointerException if the cause is {@code null}
         */
        public RecentCause {
            Objects.requireNonNull(cause, "cause");
        }
    }

    /**
     * Counters of one kind of cause.
     *
     * @param name kind, such as {@code failures} or {@code discards}
     * @param values non-zero counters by enum name, in declaration order
     */
    public record CounterGroup(String name, Map<String, Long> values) {
        /** Copies the values, keeping their order. */
        public CounterGroup {
            Objects.requireNonNull(name, "name");
            values = ordered(values);
        }
    }

    /**
     * Counters of the perception inputs.
     *
     * @param recordedEvents context events recorded
     * @param repeatedEvents context events dropped as repeats
     * @param pendingMarks pending-context marks
     * @param memberPaths member path computations
     * @param signalsPending {@code predictordecide} marks waiting for the coordinator
     * @param signalsDropped {@code predictordecide} marks dropped because the queue was full
     */
    public record Context(long recordedEvents, long repeatedEvents, long pendingMarks, long memberPaths,
                          int signalsPending, long signalsDropped) {
    }

    /**
     * Counters of the enriched threat (M11).
     *
     * @param assists assist gains sent
     * @param blockedHits blocked-hit gains sent
     * @param rangedHarass harass gains sent
     * @param decays unreachable-target losses sent
     * @param limited gains refused by a spent or full window
     * @param failures changes whose call threw
     * @param pointsAdded points sent to add
     * @param pointsRemoved points asked to remove
     */
    public record Threat(long assists, long blockedHits, long rangedHarass, long decays, long limited, long failures,
                         double pointsAdded, double pointsRemoved) {
    }
}
