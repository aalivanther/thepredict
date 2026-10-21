package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.FitGate;
import akyrion.thepredict.domain.OptionSet;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.ZoneCaps;

import java.util.Objects;

/**
 * Limits and times of the coordinator, translated by the root from {@code config.DecisionSettings} and
 * {@code config.JevSettings} (rule D10), because {@code engine} does not depend on {@code config}. Immutable.
 *
 * @param maxRequestsPerMinute server-wide requests in any 60 s window, {@code >= 1}
 * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window, 1 to {@code maxRequestsPerMinute}
 * @param maxInFlightRequests simultaneous requests of the server, {@code >= 1}
 * @param maxInFlightRequestsPerWorld simultaneous requests of one world, 1 to {@code maxInFlightRequests}
 * @param maxInputTokensPerMinute estimated input tokens in any 60 s window, {@code >= 1}
 * @param maxInstallsPerTick goal installations per tick (step 5), {@code >= 1}
 * @param maxEncounterEvaluationsPerTick encounter evaluations per tick (step 6), {@code >= 1}
 * @param maxProbesPerTick cheap probes per tick (step 4), {@code >= 1}
 * @param maxCompletionsPerTick inbox entries handled per tick (step 1), {@code >= 1}
 * @param maxSignalsPerTick context signals handled per tick (step 3), {@code >= 1}
 * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick, {@code >= 1}
 * @param requestTimeoutNanos HTTP request timeout of the client ({@code jev.timeoutMillis}), {@code > 0}
 * @param maxOptionsPerQuestion most options of one question after removing identical tactics
 *        ({@code decision.maxOptionsPerQuestion}, M16.1), {@value OptionSet#MIN_MAX_OPTIONS} to
 *        {@value OptionSet#MAX_MAX_OPTIONS}
 * @param fitThresholds veto and rescue thresholds of the fit checks ({@code decision.fitVeto},
 *        {@code decision.fitRescue}, M16.5)
 * @param maxSessionsPerTick visits of the tick layer per tick, 8 to 1024 ({@code decision.tickLayer.maxSessionsPerTick},
 *        M20.3)
 * @param tickLayerMaxMicros microseconds of the tick layer, 100 to 5000
 * @param guardMspt MSPT guard, 0 or 20 to 50
 * @param microMinHoldTicks minimum ticks of one micro-behaviour, 1 to 40
 * @param waypointRefreshTicks ticks between orbit waypoints, 2 to 40
 * @param maxWaypointPathsPerTick paths per tick, 0 to 64
 * @param maxWaypointPathMicrosPerTick microseconds of paths per tick, 0 to 5000
 * @param candidateScoring how candidates are scored (M20.6)
 * @param candidateDiversity whether each movement is shown first (M20.6)
 * @param pruning whether questions drop and reorder options ({@code decision.pruning}, M19.6)
 * @param flushIntervalTicks ticks between aggregate flushes, 20 to 1200
 * @param maxPendingKeys pending aggregate keys, 256 to 65536
 * @param maxStorageRepliesPerTick storage replies applied per tick, 1 to 64
 * @param answerCache whether answers may be reused
 * @param answerCacheTtlTicks validity of a cached answer, 20 to 600
 * @param answerCacheEntries most cached answers, 64 to 65536
 * @param answerCacheRevalidateRatio fraction of hits that are also sent, 0 to 1
 * @param difficulty target-time knobs (M22)
 * @param zone global zone limits (M24)
 * @param group global group limits (M25)
 * @param patterns global pattern limits (M26)
 * @param memory global player-memory limits (M27)
 */
public record EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                             int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                             int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                             int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                             int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds, int maxSessionsPerTick,
                             int tickLayerMaxMicros, int guardMspt, int microMinHoldTicks, int waypointRefreshTicks,
                             int maxWaypointPathsPerTick, int maxWaypointPathMicrosPerTick,
                             CandidateScoring candidateScoring, boolean candidateDiversity, PruningMode pruning,
                             int flushIntervalTicks, int maxPendingKeys, int maxStorageRepliesPerTick,
                             AnswerCacheMode answerCache, int answerCacheTtlTicks, int answerCacheEntries,
                             double answerCacheRevalidateRatio, DifficultyGlobals difficulty, ZoneCaps zone,
                             akyrion.thepredict.domain.GroupCaps group, PatternSettings patterns,
                             akyrion.thepredict.domain.MemorySettings memory) {
    /** Extra time after the request timeout before the coordinator releases a request itself (M09.8). */
    public static final long WATCHDOG_MARGIN_NANOS = 1_000_000_000L;
    /** Fewest tick-layer visits per tick. */
    public static final int MIN_SESSIONS = 8;
    /** Most tick-layer visits per tick. */
    public static final int MAX_SESSIONS = 1024;
    /** Visits per tick of a fresh installation. */
    public static final int DEFAULT_SESSIONS = 96;
    /** @deprecated the M19 cap. A compatibility constructor still maps this number onto {@link #maxSessionsPerTick}. */
    @Deprecated
    public static final int MIN_READY_CHECKS = MIN_SESSIONS;
    /** @deprecated the M19 cap. */
    @Deprecated
    public static final int MAX_READY_CHECKS = 512;
    /** @deprecated the M19 cap. Compatibility constructors keep it so a test that passed 64 still caps at 64. */
    @Deprecated
    public static final int DEFAULT_READY_CHECKS = 64;

    /**
     * Validates every limit.
     *
     * @throws IllegalArgumentException if a limit is not positive, a per-world limit exceeds the global one or a
     *         tick-layer cap is out of range
     * @throws NullPointerException if the fit thresholds, the scoring or the pruning mode are {@code null}
     */
    public EngineSettings {
        positive(maxRequestsPerMinute, "maxRequestsPerMinute");
        positive(maxRequestsPerMinutePerWorld, "maxRequestsPerMinutePerWorld");
        positive(maxInFlightRequests, "maxInFlightRequests");
        positive(maxInFlightRequestsPerWorld, "maxInFlightRequestsPerWorld");
        positive(maxInputTokensPerMinute, "maxInputTokensPerMinute");
        positive(maxInstallsPerTick, "maxInstallsPerTick");
        positive(maxEncounterEvaluationsPerTick, "maxEncounterEvaluationsPerTick");
        positive(maxProbesPerTick, "maxProbesPerTick");
        positive(maxCompletionsPerTick, "maxCompletionsPerTick");
        positive(maxSignalsPerTick, "maxSignalsPerTick");
        positive(maxTickWorkMicros, "maxTickWorkMicros");
        if (requestTimeoutNanos <= 0) {
            throw new IllegalArgumentException("requestTimeoutNanos must be > 0, was " + requestTimeoutNanos);
        }
        if (maxRequestsPerMinutePerWorld > maxRequestsPerMinute || maxInFlightRequestsPerWorld > maxInFlightRequests) {
            throw new IllegalArgumentException("a per-world limit may not exceed the server limit");
        }
        if (maxOptionsPerQuestion < OptionSet.MIN_MAX_OPTIONS || maxOptionsPerQuestion > OptionSet.MAX_MAX_OPTIONS) {
            throw new IllegalArgumentException("maxOptionsPerQuestion must be within [" + OptionSet.MIN_MAX_OPTIONS
                    + ", " + OptionSet.MAX_MAX_OPTIONS + "], was " + maxOptionsPerQuestion);
        }
        Objects.requireNonNull(fitThresholds, "fitThresholds");
        if (maxSessionsPerTick < MIN_SESSIONS || maxSessionsPerTick > MAX_SESSIONS) {
            throw new IllegalArgumentException("maxSessionsPerTick must be within [" + MIN_SESSIONS + ", "
                    + MAX_SESSIONS + "], was " + maxSessionsPerTick);
        }
        if (tickLayerMaxMicros < 100 || tickLayerMaxMicros > 5000) {
            throw new IllegalArgumentException("tickLayerMaxMicros must be within [100, 5000], was "
                    + tickLayerMaxMicros);
        }
        if (guardMspt != 0 && (guardMspt < 20 || guardMspt > 50)) {
            throw new IllegalArgumentException("guardMspt must be 0 or within [20, 50], was " + guardMspt);
        }
        if (microMinHoldTicks < 1 || microMinHoldTicks > 40) {
            throw new IllegalArgumentException("microMinHoldTicks must be within [1, 40], was " + microMinHoldTicks);
        }
        if (waypointRefreshTicks < 2 || waypointRefreshTicks > 40) {
            throw new IllegalArgumentException("waypointRefreshTicks must be within [2, 40], was "
                    + waypointRefreshTicks);
        }
        if (maxWaypointPathsPerTick < 0 || maxWaypointPathsPerTick > 64) {
            throw new IllegalArgumentException("maxWaypointPathsPerTick must be within [0, 64], was "
                    + maxWaypointPathsPerTick);
        }
        if (maxWaypointPathMicrosPerTick < 0 || maxWaypointPathMicrosPerTick > 5000) {
            throw new IllegalArgumentException("maxWaypointPathMicrosPerTick must be within [0, 5000], was "
                    + maxWaypointPathMicrosPerTick);
        }
        Objects.requireNonNull(candidateScoring, "candidateScoring");
        Objects.requireNonNull(pruning, "pruning");
        if (flushIntervalTicks < 20 || flushIntervalTicks > 1200) {
            throw new IllegalArgumentException("flushIntervalTicks must be within [20, 1200], was "
                    + flushIntervalTicks);
        }
        if (maxPendingKeys < 256 || maxPendingKeys > 65536) {
            throw new IllegalArgumentException("maxPendingKeys must be within [256, 65536], was " + maxPendingKeys);
        }
        if (maxStorageRepliesPerTick < 1 || maxStorageRepliesPerTick > 64) {
            throw new IllegalArgumentException("maxStorageRepliesPerTick must be within [1, 64], was "
                    + maxStorageRepliesPerTick);
        }
        Objects.requireNonNull(answerCache, "answerCache");
        if (answerCacheTtlTicks < 20 || answerCacheTtlTicks > 600) {
            throw new IllegalArgumentException("answerCacheTtlTicks must be within [20, 600], was "
                    + answerCacheTtlTicks);
        }
        if (answerCacheEntries < 64 || answerCacheEntries > 65536) {
            throw new IllegalArgumentException("answerCacheEntries must be within [64, 65536], was "
                    + answerCacheEntries);
        }
        if (!(answerCacheRevalidateRatio >= 0.0) || answerCacheRevalidateRatio > 1.0) {
            throw new IllegalArgumentException("answerCacheRevalidateRatio must be within [0, 1], was "
                    + answerCacheRevalidateRatio);
        }
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(memory, "memory");
    }

    /**
     * Settings from before the pattern limits were a component (rule W5). Patterns use
     * {@link PatternSettings#DEFAULT}.
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos request timeout
     * @param maxOptionsPerQuestion options of one question
     * @param fitThresholds fit thresholds
     * @param maxSessionsPerTick tick-layer visits
     * @param tickLayerMaxMicros tick-layer budget
     * @param guardMspt MSPT guard
     * @param microMinHoldTicks minimum ticks a micro stays
     * @param waypointRefreshTicks ticks between waypoint refreshes
     * @param maxWaypointPathsPerTick waypoint paths per tick
     * @param maxWaypointPathMicrosPerTick time cap of waypoint paths
     * @param candidateScoring how candidates are scored
     * @param candidateDiversity whether each movement is shown first
     * @param pruning pruning mode
     * @param flushIntervalTicks ticks between aggregate flushes
     * @param maxPendingKeys pending aggregate keys
     * @param maxStorageRepliesPerTick storage replies applied per tick
     * @param answerCache whether answers may be reused
     * @param answerCacheTtlTicks validity of a cached answer
     * @param answerCacheEntries most cached answers
     * @param answerCacheRevalidateRatio fraction of hits that are also sent
     * @param difficulty target-time knobs
     * @param zone global zone limits
     * @param group global group limits
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                          int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds, int maxSessionsPerTick,
                          int tickLayerMaxMicros, int guardMspt, int microMinHoldTicks, int waypointRefreshTicks,
                          int maxWaypointPathsPerTick, int maxWaypointPathMicrosPerTick,
                          CandidateScoring candidateScoring, boolean candidateDiversity, PruningMode pruning,
                          int flushIntervalTicks, int maxPendingKeys, int maxStorageRepliesPerTick,
                          AnswerCacheMode answerCache, int answerCacheTtlTicks, int answerCacheEntries,
                          double answerCacheRevalidateRatio, DifficultyGlobals difficulty, ZoneCaps zone,
                          akyrion.thepredict.domain.GroupCaps group) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos, maxOptionsPerQuestion,
                fitThresholds, maxSessionsPerTick, tickLayerMaxMicros, guardMspt, microMinHoldTicks,
                waypointRefreshTicks, maxWaypointPathsPerTick, maxWaypointPathMicrosPerTick, candidateScoring,
                candidateDiversity, pruning, flushIntervalTicks, maxPendingKeys, maxStorageRepliesPerTick, answerCache,
                answerCacheTtlTicks, answerCacheEntries, answerCacheRevalidateRatio, difficulty, zone, group,
                PatternSettings.DEFAULT, akyrion.thepredict.domain.MemorySettings.DEFAULT);
    }

    /**
     * Settings from before player memory (rule W5). Memory uses its delivered default.
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos request timeout
     * @param maxOptionsPerQuestion options of one question
     * @param fitThresholds fit thresholds
     * @param maxSessionsPerTick tick-layer visits
     * @param tickLayerMaxMicros tick-layer budget
     * @param guardMspt MSPT guard
     * @param microMinHoldTicks minimum ticks a micro stays
     * @param waypointRefreshTicks ticks between waypoint refreshes
     * @param maxWaypointPathsPerTick waypoint paths per tick
     * @param maxWaypointPathMicrosPerTick time cap of waypoint paths
     * @param candidateScoring how candidates are scored
     * @param candidateDiversity whether each movement is shown first
     * @param pruning pruning mode
     * @param flushIntervalTicks ticks between aggregate flushes
     * @param maxPendingKeys pending aggregate keys
     * @param maxStorageRepliesPerTick storage replies applied per tick
     * @param answerCache whether answers may be reused
     * @param answerCacheTtlTicks validity of a cached answer
     * @param answerCacheEntries most cached answers
     * @param answerCacheRevalidateRatio fraction of hits that are also sent
     * @param difficulty target-time knobs
     * @param zone global zone limits
     * @param group global group limits
     * @param patterns global pattern limits
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                          int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds, int maxSessionsPerTick,
                          int tickLayerMaxMicros, int guardMspt, int microMinHoldTicks, int waypointRefreshTicks,
                          int maxWaypointPathsPerTick, int maxWaypointPathMicrosPerTick,
                          CandidateScoring candidateScoring, boolean candidateDiversity, PruningMode pruning,
                          int flushIntervalTicks, int maxPendingKeys, int maxStorageRepliesPerTick,
                          AnswerCacheMode answerCache, int answerCacheTtlTicks, int answerCacheEntries,
                          double answerCacheRevalidateRatio, DifficultyGlobals difficulty, ZoneCaps zone,
                          akyrion.thepredict.domain.GroupCaps group, PatternSettings patterns) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos, maxOptionsPerQuestion,
                fitThresholds, maxSessionsPerTick, tickLayerMaxMicros, guardMspt, microMinHoldTicks,
                waypointRefreshTicks, maxWaypointPathsPerTick, maxWaypointPathMicrosPerTick, candidateScoring,
                candidateDiversity, pruning, flushIntervalTicks, maxPendingKeys, maxStorageRepliesPerTick, answerCache,
                answerCacheTtlTicks, answerCacheEntries, answerCacheRevalidateRatio, difficulty, zone, group, patterns,
                akyrion.thepredict.domain.MemorySettings.DEFAULT);
    }

    /**
     * Settings from before the zone caps were a component (rule W5). The zone uses {@link ZoneCaps#DEFAULT}.
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos HTTP request timeout of the client
     * @param maxOptionsPerQuestion most options of one question
     * @param fitThresholds veto and rescue thresholds of the fit checks
     * @param maxSessionsPerTick sessions the tick layer may visit
     * @param tickLayerMaxMicros time cap of one pass
     * @param guardMspt MSPT guard, or 0
     * @param microMinHoldTicks minimum ticks a micro stays
     * @param waypointRefreshTicks ticks between waypoint refreshes
     * @param maxWaypointPathsPerTick waypoint paths per tick
     * @param maxWaypointPathMicrosPerTick time cap of waypoint paths
     * @param candidateScoring how candidates are scored
     * @param candidateDiversity whether each movement is shown first
     * @param pruning pruning mode
     * @param flushIntervalTicks ticks between aggregate flushes
     * @param maxPendingKeys pending aggregate keys
     * @param maxStorageRepliesPerTick storage replies applied per tick
     * @param answerCache whether answers may be reused
     * @param answerCacheTtlTicks validity of a cached answer
     * @param answerCacheEntries most cached answers
     * @param answerCacheRevalidateRatio fraction of hits that are also sent
     * @param difficulty target-time knobs
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                          int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds, int maxSessionsPerTick,
                          int tickLayerMaxMicros, int guardMspt, int microMinHoldTicks, int waypointRefreshTicks,
                          int maxWaypointPathsPerTick, int maxWaypointPathMicrosPerTick,
                          CandidateScoring candidateScoring, boolean candidateDiversity, PruningMode pruning,
                          int flushIntervalTicks, int maxPendingKeys, int maxStorageRepliesPerTick,
                          AnswerCacheMode answerCache, int answerCacheTtlTicks, int answerCacheEntries,
                          double answerCacheRevalidateRatio, DifficultyGlobals difficulty) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos, maxOptionsPerQuestion,
                fitThresholds, maxSessionsPerTick, tickLayerMaxMicros, guardMspt, microMinHoldTicks,
                waypointRefreshTicks, maxWaypointPathsPerTick, maxWaypointPathMicrosPerTick, candidateScoring,
                candidateDiversity, pruning, flushIntervalTicks, maxPendingKeys, maxStorageRepliesPerTick, answerCache,
                answerCacheTtlTicks, answerCacheEntries, answerCacheRevalidateRatio, difficulty, ZoneCaps.DEFAULT,
                akyrion.thepredict.domain.GroupCaps.DEFAULT);
    }

    /**
     * Settings from before the group section (rule W5). The group uses {@link akyrion.thepredict.domain.GroupCaps#DEFAULT}.
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos request timeout
     * @param maxOptionsPerQuestion options of one question
     * @param fitThresholds fit thresholds
     * @param maxSessionsPerTick tick-layer visits
     * @param tickLayerMaxMicros tick-layer budget
     * @param guardMspt MSPT guard
     * @param microMinHoldTicks minimum ticks a micro stays
     * @param waypointRefreshTicks ticks between waypoint refreshes
     * @param maxWaypointPathsPerTick waypoint paths per tick
     * @param maxWaypointPathMicrosPerTick time cap of waypoint paths
     * @param candidateScoring how candidates are scored
     * @param candidateDiversity whether each movement is shown first
     * @param pruning pruning mode
     * @param flushIntervalTicks ticks between aggregate flushes
     * @param maxPendingKeys pending aggregate keys
     * @param maxStorageRepliesPerTick storage replies applied per tick
     * @param answerCache whether answers may be reused
     * @param answerCacheTtlTicks validity of a cached answer
     * @param answerCacheEntries most cached answers
     * @param answerCacheRevalidateRatio fraction of hits that are also sent
     * @param difficulty target-time knobs
     * @param zone global zone limits
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                          int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds, int maxSessionsPerTick,
                          int tickLayerMaxMicros, int guardMspt, int microMinHoldTicks, int waypointRefreshTicks,
                          int maxWaypointPathsPerTick, int maxWaypointPathMicrosPerTick,
                          CandidateScoring candidateScoring, boolean candidateDiversity, PruningMode pruning,
                          int flushIntervalTicks, int maxPendingKeys, int maxStorageRepliesPerTick,
                          AnswerCacheMode answerCache, int answerCacheTtlTicks, int answerCacheEntries,
                          double answerCacheRevalidateRatio, DifficultyGlobals difficulty, ZoneCaps zone) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos, maxOptionsPerQuestion,
                fitThresholds, maxSessionsPerTick, tickLayerMaxMicros, guardMspt, microMinHoldTicks,
                waypointRefreshTicks, maxWaypointPathsPerTick, maxWaypointPathMicrosPerTick, candidateScoring,
                candidateDiversity, pruning, flushIntervalTicks, maxPendingKeys, maxStorageRepliesPerTick, answerCache,
                answerCacheTtlTicks, answerCacheEntries, answerCacheRevalidateRatio, difficulty, zone,
                akyrion.thepredict.domain.GroupCaps.DEFAULT);
    }

    /**
     * Settings of the tick layer without storage overrides (rule W5). Storage stays at the delivered defaults.
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos HTTP request timeout of the client
     * @param maxOptionsPerQuestion most options of one question
     * @param fitThresholds veto and rescue thresholds of the fit checks
     * @param maxSessionsPerTick sessions the tick layer may visit
     * @param tickLayerMaxMicros time cap of one pass
     * @param guardMspt MSPT guard, or 0
     * @param microMinHoldTicks minimum ticks a micro stays
     * @param waypointRefreshTicks ticks between waypoint refreshes
     * @param maxWaypointPathsPerTick waypoint paths per tick
     * @param maxWaypointPathMicrosPerTick time cap of waypoint paths
     * @param candidateScoring how candidates are scored
     * @param candidateDiversity whether each movement is shown first
     * @param pruning pruning mode
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                          int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds, int maxSessionsPerTick,
                          int tickLayerMaxMicros, int guardMspt, int microMinHoldTicks, int waypointRefreshTicks,
                          int maxWaypointPathsPerTick, int maxWaypointPathMicrosPerTick,
                          CandidateScoring candidateScoring, boolean candidateDiversity, PruningMode pruning) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos, maxOptionsPerQuestion,
                fitThresholds, maxSessionsPerTick, tickLayerMaxMicros, guardMspt, microMinHoldTicks,
                waypointRefreshTicks, maxWaypointPathsPerTick, maxWaypointPathMicrosPerTick, candidateScoring,
                candidateDiversity, pruning, 100, 4096, 4, AnswerCacheMode.OFF, 100, 2048, 0.1,
                DifficultyGlobals.DEFAULT);
    }

    /**
     * Settings of M19. {@code maxReadyChecksPerTick} is mapped onto {@code maxSessionsPerTick} (rule W5).
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos HTTP request timeout of the client
     * @param maxOptionsPerQuestion most options of one question
     * @param fitThresholds veto and rescue thresholds of the fit checks
     * @param maxReadyChecksPerTick obsolete cap, used only as the session cap
     * @param pruning pruning mode
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                          int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds, int maxReadyChecksPerTick,
                          PruningMode pruning) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos, maxOptionsPerQuestion,
                fitThresholds, maxReadyChecksPerTick, 600, 0, 4, 5, 8, 400, CandidateScoring.DEFAULT, false, pruning,
                100, 4096, 4, AnswerCacheMode.OFF, 100, 2048, 0.1, DifficultyGlobals.DEFAULT);
    }

    /**
     * Creates the settings with the M19 ready-check cap of 64 and pruning off (rule W5). Production passes the
     * tick-layer caps.
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos HTTP request timeout of the client
     * @param maxOptionsPerQuestion most options of one question
     * @param fitThresholds veto and rescue thresholds of the fit checks
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos,
                          int maxOptionsPerQuestion, FitGate.FitThresholds fitThresholds) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos, maxOptionsPerQuestion,
                fitThresholds, DEFAULT_READY_CHECKS, PruningMode.DEFAULT);
    }

    /**
     * Creates the settings with the default option cap and fit thresholds (rule W5: the form of every setting before
     * M16). Production code passes the option cap and the fit thresholds of {@code decision.questions}; this
     * constructor stays only while tests use it.
     *
     * @param maxRequestsPerMinute server-wide requests in any 60 s window
     * @param maxRequestsPerMinutePerWorld requests of one world in any 60 s window
     * @param maxInFlightRequests simultaneous requests of the server
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute estimated input tokens in any 60 s window
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick cheap probes per tick
     * @param maxCompletionsPerTick inbox entries handled per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work after which steps 5 and 6 wait for the next tick
     * @param requestTimeoutNanos HTTP request timeout of the client
     * @throws IllegalArgumentException if a limit is invalid
     */
    public EngineSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                          int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int maxInstallsPerTick,
                          int maxEncounterEvaluationsPerTick, int maxProbesPerTick, int maxCompletionsPerTick,
                          int maxSignalsPerTick, int maxTickWorkMicros, long requestTimeoutNanos) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, requestTimeoutNanos,
                OptionSet.DEFAULT_MAX_OPTIONS, FitGate.FitThresholds.DEFAULT, DEFAULT_READY_CHECKS, PruningMode.DEFAULT);
    }

    /**
     * Returns the capacity of the inbox: one result per request in flight twice over, plus the service checks.
     *
     * @return {@code 2 × maxInFlightRequests + 2}
     */
    public int inboxCapacity() {
        return 2 * maxInFlightRequests + 2;
    }

    /**
     * Returns how long a request may stay in flight before the coordinator releases it as {@code TIMEOUT}.
     *
     * @return {@code requestTimeoutNanos + }{@link #WATCHDOG_MARGIN_NANOS}
     */
    public long watchdogNanos() {
        return requestTimeoutNanos + WATCHDOG_MARGIN_NANOS;
    }

    /**
     * Returns the work budget of one tick.
     *
     * @return {@code maxTickWorkMicros} in nanoseconds
     */
    public long maxTickWorkNanos() {
        return maxTickWorkMicros * 1_000L;
    }

    private static void positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1, was " + value);
        }
    }
}
