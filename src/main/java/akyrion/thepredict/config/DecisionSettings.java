package akyrion.thepredict.config;

import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.CastThread;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.UntypedCastMode;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The {@code decision.*} section of {@code config.yml} plus {@code debug.recordScenarios}: global limits
 * and thresholds that a profile never widens (M03.1). The root translates them into
 * {@code engine.EngineSettings} (rule D10).
 *
 * @param maxRequestsPerMinute server-wide requests per minute, 1 to 1200
 * @param maxRequestsPerMinutePerWorld requests per minute of one world, 1 to {@code maxRequestsPerMinute}
 * @param maxInFlightRequests simultaneous requests, 1 to 64
 * @param maxInFlightRequestsPerWorld simultaneous requests of one world, 1 to {@code maxInFlightRequests}
 * @param maxInputTokensPerMinute input tokens per minute, 1000 to 10 000 000
 * @param encounterHeartbeatTicks ticks between routine decisions of an encounter, 10 to 200
 * @param contingenciesPerMob contingency questions per mob, 0 to 2
 * @param minimumConfidence default confidence required to adopt an answer, 0 to 0.99
 * @param tacticSwitchMargin default probability margin required to switch tactic, 0 to 0.99
 * @param maxInstallsPerTick goal installations per tick, 1 to 100
 * @param maxEncounterEvaluationsPerTick encounter evaluations per tick, 1 to 64
 * @param maxProbesPerTick session probes per tick, 8 to 1024
 * @param maxCompletionsPerTick answers applied per tick, 1 to 256
 * @param maxSignalsPerTick context signals handled per tick, 1 to 1024
 * @param maxTickWorkMicros work budget per tick, 100 to 20 000 microseconds
 * @param maxDecisionAgeMillis oldest answer that may still be applied, 100 to 5000 ms
 * @param lowConfidenceStreakForLocal low-confidence answers in a row before local mode, 1 to 20
 * @param searchRadiusBlocks cap on the perception radius over {@code FOLLOW_RANGE}, 8 to 128 blocks
 * @param logDecisions whether each decision is logged
 * @param recordScenarios {@code debug.recordScenarios}: whether decisions are recorded as aliased scenarios (M13)
 * @param questions option cap, question detail and fit checks of each question (M16)
 * @param castMode which skills fire as soon as they are ready ({@code decision.castMode}, M19.5)
 * @param castThread thread of a cast ({@code decision.castThread}, M19.4); read only by the execution adapter
 * @param untypedCast whether a choice without a purpose may fire alone ({@code decision.untypedCast}, M19.2)
 * @param readyHorizonTicks ticks ahead a skill counts as ready for the question, 0 to 100
 * @param tickLayerMode global tick layer ({@code decision.tickLayer.mode}, M20.3)
 * @param maxSessionsPerTick visits of the tick layer per tick, 8 to 1024
 * @param tickLayerMaxMicros microseconds of the tick layer, 100 to 5000
 * @param guardMspt MSPT guard, 0 or 20 to 50
 * @param microMinHoldTicks minimum ticks of one micro-behaviour, 1 to 40
 * @param waypointRefreshTicks ticks between orbit waypoints, 2 to 40
 * @param maxWaypointPathsPerTick paths per tick, 0 to 64
 * @param maxWaypointPathMicrosPerTick microseconds of paths per tick, 0 to 5000
 * @param candidateScoring how candidates are scored (M20.6)
 * @param candidateDiversity whether each movement is shown first (M20.6)
 * @param debugSeed base seed of {@link akyrion.thepredict.domain.StableRandom} ({@code debug.seed}, M20.1)
 * @param pruning whether questions drop and reorder options ({@code decision.pruning}, M19.6)
 * @param confidenceGate raw or calibrated threshold ({@code decision.confidenceGate}, M28.4)
 * @param calibratedMinimum how the calibrated threshold is chosen (M28.4)
 */
public record DecisionSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                               int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute,
                               int encounterHeartbeatTicks, int contingenciesPerMob, double minimumConfidence,
                               double tacticSwitchMargin, int maxInstallsPerTick, int maxEncounterEvaluationsPerTick,
                               int maxProbesPerTick, int maxCompletionsPerTick, int maxSignalsPerTick,
                               int maxTickWorkMicros, int maxDecisionAgeMillis, int lowConfidenceStreakForLocal,
                               double searchRadiusBlocks, boolean logDecisions, boolean recordScenarios,
                               QuestionSettings questions, CastMode castMode, CastThread castThread,
                               UntypedCastMode untypedCast, int readyHorizonTicks, TickLayerMode tickLayerMode,
                               int maxSessionsPerTick, int tickLayerMaxMicros, int guardMspt, int microMinHoldTicks,
                               int waypointRefreshTicks, int maxWaypointPathsPerTick, int maxWaypointPathMicrosPerTick,
                               CandidateScoring candidateScoring, boolean candidateDiversity, long debugSeed,
                               PruningMode pruning, akyrion.thepredict.domain.learn.ConfidenceGate confidenceGate,
                               akyrion.thepredict.domain.learn.CalibratedMinimum calibratedMinimum) {
    /** Largest accepted confidence and switch margin. */
    public static final double MAX_RATIO = 0.99;
    /** Fewest tick-layer visits per tick. */
    public static final int MIN_SESSIONS = 8;
    /** Most tick-layer visits per tick. */
    public static final int MAX_SESSIONS = 1024;
    /** Visits per tick of a fresh installation. */
    public static final int DEFAULT_SESSIONS = 96;
    /** @deprecated the M19 cap; accepted and ignored. Kept so older call sites name the same bounds. */
    @Deprecated
    public static final int MIN_READY_CHECKS = MIN_SESSIONS;
    /** @deprecated the M19 cap. */
    @Deprecated
    public static final int MAX_READY_CHECKS = 512;
    /** @deprecated the M19 cap. A compatibility constructor still maps this number onto {@link #maxSessionsPerTick}. */
    @Deprecated
    public static final int DEFAULT_READY_CHECKS = 64;
    /** Delivered {@code decision.tickLayer.maxMicros} (C13: the only place of this number). */
    public static final int DEFAULT_TICK_LAYER_MAX_MICROS = 600;
    /** Delivered {@code decision.tickLayer.guardMspt}: off. */
    public static final int DEFAULT_GUARD_MSPT = 0;
    /** Delivered {@code decision.tickLayer.microMinHoldTicks}. */
    public static final int DEFAULT_MICRO_MIN_HOLD_TICKS = 4;
    /** Delivered {@code decision.tickLayer.waypointRefreshTicks}. */
    public static final int DEFAULT_WAYPOINT_REFRESH_TICKS = 5;
    /** Delivered {@code decision.tickLayer.maxWaypointPathsPerTick}. */
    public static final int DEFAULT_MAX_WAYPOINT_PATHS_PER_TICK = 8;
    /** Delivered {@code decision.tickLayer.maxWaypointPathMicrosPerTick}. */
    public static final int DEFAULT_MAX_WAYPOINT_PATH_MICROS_PER_TICK = 400;

    /**
     * Validates every value.
     *
     * @throws ConfigurationException if a value is outside its range, naming its path
     */
    public DecisionSettings {
        SettingsChecks.requireRange(maxRequestsPerMinute, 1, 1200, "decision.maxRequestsPerMinute");
        SettingsChecks.requireRange(maxRequestsPerMinutePerWorld, 1, maxRequestsPerMinute,
                "decision.maxRequestsPerMinutePerWorld");
        SettingsChecks.requireRange(maxInFlightRequests, 1, 64, "decision.maxInFlightRequests");
        SettingsChecks.requireRange(maxInFlightRequestsPerWorld, 1, maxInFlightRequests,
                "decision.maxInFlightRequestsPerWorld");
        SettingsChecks.requireRange(maxInputTokensPerMinute, 1000, 10_000_000, "decision.maxInputTokensPerMinute");
        SettingsChecks.requireRange(encounterHeartbeatTicks, 10, 200, "decision.encounterHeartbeatTicks");
        SettingsChecks.requireRange(contingenciesPerMob, 0, 2, "decision.contingenciesPerMob");
        SettingsChecks.requireRange(minimumConfidence, 0.0, MAX_RATIO, "decision.minimumConfidence");
        SettingsChecks.requireRange(tacticSwitchMargin, 0.0, MAX_RATIO, "decision.tacticSwitchMargin");
        SettingsChecks.requireRange(maxInstallsPerTick, 1, 100, "decision.maxInstallsPerTick");
        SettingsChecks.requireRange(maxEncounterEvaluationsPerTick, 1, 64, "decision.maxEncounterEvaluationsPerTick");
        SettingsChecks.requireRange(maxProbesPerTick, 8, 1024, "decision.maxProbesPerTick");
        SettingsChecks.requireRange(maxCompletionsPerTick, 1, 256, "decision.maxCompletionsPerTick");
        SettingsChecks.requireRange(maxSignalsPerTick, 1, 1024, "decision.maxSignalsPerTick");
        SettingsChecks.requireRange(maxTickWorkMicros, 100, 20_000, "decision.maxTickWorkMicros");
        SettingsChecks.requireRange(maxDecisionAgeMillis, 100, 5000, "decision.maxDecisionAgeMillis");
        SettingsChecks.requireRange(lowConfidenceStreakForLocal, 1, 20, "decision.lowConfidenceStreakForLocal");
        SettingsChecks.requireRange(searchRadiusBlocks, 8.0, 128.0, "decision.searchRadiusBlocks");
        Objects.requireNonNull(questions, "questions");
        Objects.requireNonNull(castMode, "castMode");
        Objects.requireNonNull(castThread, "castThread");
        Objects.requireNonNull(untypedCast, "untypedCast");
        SettingsChecks.requireRange(readyHorizonTicks, 0, 100, "decision.readyHorizonTicks");
        Objects.requireNonNull(tickLayerMode, "tickLayerMode");
        SettingsChecks.requireRange(maxSessionsPerTick, MIN_SESSIONS, MAX_SESSIONS, "decision.tickLayer.maxSessionsPerTick");
        SettingsChecks.requireRange(tickLayerMaxMicros, 100, 5000, "decision.tickLayer.maxMicros");
        if (guardMspt != 0 && (guardMspt < 20 || guardMspt > 50)) {
            throw new ConfigurationException("decision.tickLayer.guardMspt", "must be 0 or within [20, 50], was "
                    + guardMspt);
        }
        SettingsChecks.requireRange(microMinHoldTicks, 1, 40, "decision.tickLayer.microMinHoldTicks");
        SettingsChecks.requireRange(waypointRefreshTicks, 2, 40, "decision.tickLayer.waypointRefreshTicks");
        SettingsChecks.requireRange(maxWaypointPathsPerTick, 0, 64, "decision.tickLayer.maxWaypointPathsPerTick");
        SettingsChecks.requireRange(maxWaypointPathMicrosPerTick, 0, 5000,
                "decision.tickLayer.maxWaypointPathMicrosPerTick");
        Objects.requireNonNull(candidateScoring, "candidateScoring");
        Objects.requireNonNull(pruning, "pruning");
        Objects.requireNonNull(confidenceGate, "confidenceGate");
        Objects.requireNonNull(calibratedMinimum, "calibratedMinimum");
    }

    /**
     * Settings of M19 plus the tick-layer defaults. {@code maxReadyChecksPerTick} is mapped onto
     * {@code maxSessionsPerTick} so a test that passed 64 keeps that cap (rule W5).
     *
     * @param maxRequestsPerMinute server-wide requests per minute
     * @param maxRequestsPerMinutePerWorld requests per minute of one world
     * @param maxInFlightRequests simultaneous requests
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute input tokens per minute
     * @param encounterHeartbeatTicks ticks between routine decisions of an encounter
     * @param contingenciesPerMob contingency questions per mob
     * @param minimumConfidence default confidence required to adopt an answer
     * @param tacticSwitchMargin default probability margin required to switch tactic
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick session probes per tick
     * @param maxCompletionsPerTick answers applied per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work budget per tick
     * @param maxDecisionAgeMillis oldest answer that may still be applied
     * @param lowConfidenceStreakForLocal low-confidence answers in a row before local mode
     * @param searchRadiusBlocks cap on the perception radius
     * @param logDecisions whether each decision is logged
     * @param recordScenarios whether decisions are recorded as aliased scenarios
     * @param questions option cap, question detail and fit checks
     * @param castMode when a ready cast may fire
     * @param castThread which thread runs a MythicMobs cast
     * @param untypedCast how an untyped choice is treated
     * @param readyHorizonTicks ticks a ready skill may wait
     * @param maxReadyChecksPerTick obsolete cap, used only as the session cap
     * @param pruning pruning mode
     */
    public DecisionSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                            int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int encounterHeartbeatTicks,
                            int contingenciesPerMob, double minimumConfidence, double tacticSwitchMargin,
                            int maxInstallsPerTick, int maxEncounterEvaluationsPerTick, int maxProbesPerTick,
                            int maxCompletionsPerTick, int maxSignalsPerTick, int maxTickWorkMicros,
                            int maxDecisionAgeMillis, int lowConfidenceStreakForLocal, double searchRadiusBlocks,
                            boolean logDecisions, boolean recordScenarios, QuestionSettings questions, CastMode castMode,
                            CastThread castThread, UntypedCastMode untypedCast, int readyHorizonTicks,
                            int maxReadyChecksPerTick, PruningMode pruning) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, encounterHeartbeatTicks, contingenciesPerMob, minimumConfidence,
                tacticSwitchMargin, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, maxDecisionAgeMillis,
                lowConfidenceStreakForLocal, searchRadiusBlocks, logDecisions, recordScenarios, questions, castMode,
                castThread, untypedCast, readyHorizonTicks, TickLayerMode.DEFAULT, maxReadyChecksPerTick,
                DEFAULT_TICK_LAYER_MAX_MICROS, DEFAULT_GUARD_MSPT, DEFAULT_MICRO_MIN_HOLD_TICKS,
                DEFAULT_WAYPOINT_REFRESH_TICKS, DEFAULT_MAX_WAYPOINT_PATHS_PER_TICK,
                DEFAULT_MAX_WAYPOINT_PATH_MICROS_PER_TICK, CandidateScoring.DEFAULT, false, 0L, pruning,
                akyrion.thepredict.domain.learn.ConfidenceGate.DEFAULT,
                akyrion.thepredict.domain.learn.CalibratedMinimum.PARITY);
    }

    /**
     * Creates the settings with the M19 defaults (rule W5): {@code ANY_READY}, {@code SAME_TICK}, untyped choices only
     * inside their tactic, horizon 0, 64 ready checks and pruning off. The loader passes the parsed keys.
     *
     * @param maxRequestsPerMinute server-wide requests per minute
     * @param maxRequestsPerMinutePerWorld requests per minute of one world
     * @param maxInFlightRequests simultaneous requests
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute input tokens per minute
     * @param encounterHeartbeatTicks ticks between routine decisions of an encounter
     * @param contingenciesPerMob contingency questions per mob
     * @param minimumConfidence default confidence required to adopt an answer
     * @param tacticSwitchMargin default probability margin required to switch tactic
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick session probes per tick
     * @param maxCompletionsPerTick answers applied per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work budget per tick
     * @param maxDecisionAgeMillis oldest answer that may still be applied
     * @param lowConfidenceStreakForLocal low-confidence answers in a row before local mode
     * @param searchRadiusBlocks cap on the perception radius
     * @param logDecisions whether each decision is logged
     * @param recordScenarios whether decisions are recorded as aliased scenarios
     * @param questions option cap, question detail and fit checks
     */
    public DecisionSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                            int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int encounterHeartbeatTicks,
                            int contingenciesPerMob, double minimumConfidence, double tacticSwitchMargin,
                            int maxInstallsPerTick, int maxEncounterEvaluationsPerTick, int maxProbesPerTick,
                            int maxCompletionsPerTick, int maxSignalsPerTick, int maxTickWorkMicros,
                            int maxDecisionAgeMillis, int lowConfidenceStreakForLocal, double searchRadiusBlocks,
                            boolean logDecisions, boolean recordScenarios, QuestionSettings questions) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, encounterHeartbeatTicks, contingenciesPerMob, minimumConfidence,
                tacticSwitchMargin, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, maxDecisionAgeMillis,
                lowConfidenceStreakForLocal, searchRadiusBlocks, logDecisions, recordScenarios, questions,
                CastMode.DEFAULT, CastThread.DEFAULT, UntypedCastMode.DEFAULT, 0, TickLayerMode.DEFAULT,
                DEFAULT_SESSIONS, DEFAULT_TICK_LAYER_MAX_MICROS, DEFAULT_GUARD_MSPT, DEFAULT_MICRO_MIN_HOLD_TICKS,
                DEFAULT_WAYPOINT_REFRESH_TICKS, DEFAULT_MAX_WAYPOINT_PATHS_PER_TICK,
                DEFAULT_MAX_WAYPOINT_PATH_MICROS_PER_TICK, CandidateScoring.DEFAULT, false, 0L, PruningMode.DEFAULT,
                akyrion.thepredict.domain.learn.ConfidenceGate.DEFAULT,
                akyrion.thepredict.domain.learn.CalibratedMinimum.PARITY);
    }

    /**
     * Creates the settings with the default question settings of M16 (rule W5). The loader passes the parsed
     * {@code decision.questions}; this constructor stays only while {@link #standard()}, the migration of schema 1
     * (which carries no question settings) and tests use it, and retires with that migration.
     *
     * @param maxRequestsPerMinute server-wide requests per minute
     * @param maxRequestsPerMinutePerWorld requests per minute of one world
     * @param maxInFlightRequests simultaneous requests
     * @param maxInFlightRequestsPerWorld simultaneous requests of one world
     * @param maxInputTokensPerMinute input tokens per minute
     * @param encounterHeartbeatTicks ticks between routine decisions of an encounter
     * @param contingenciesPerMob contingency questions per mob
     * @param minimumConfidence default confidence required to adopt an answer
     * @param tacticSwitchMargin default probability margin required to switch tactic
     * @param maxInstallsPerTick goal installations per tick
     * @param maxEncounterEvaluationsPerTick encounter evaluations per tick
     * @param maxProbesPerTick session probes per tick
     * @param maxCompletionsPerTick answers applied per tick
     * @param maxSignalsPerTick context signals handled per tick
     * @param maxTickWorkMicros work budget per tick
     * @param maxDecisionAgeMillis oldest answer that may still be applied
     * @param lowConfidenceStreakForLocal low-confidence answers in a row before local mode
     * @param searchRadiusBlocks cap on the perception radius
     * @param logDecisions whether each decision is logged
     * @param recordScenarios whether decisions are recorded as aliased scenarios
     * @throws ConfigurationException if a value is outside its range
     */
    public DecisionSettings(int maxRequestsPerMinute, int maxRequestsPerMinutePerWorld, int maxInFlightRequests,
                            int maxInFlightRequestsPerWorld, int maxInputTokensPerMinute, int encounterHeartbeatTicks,
                            int contingenciesPerMob, double minimumConfidence, double tacticSwitchMargin,
                            int maxInstallsPerTick, int maxEncounterEvaluationsPerTick, int maxProbesPerTick,
                            int maxCompletionsPerTick, int maxSignalsPerTick, int maxTickWorkMicros,
                            int maxDecisionAgeMillis, int lowConfidenceStreakForLocal, double searchRadiusBlocks,
                            boolean logDecisions, boolean recordScenarios) {
        this(maxRequestsPerMinute, maxRequestsPerMinutePerWorld, maxInFlightRequests, maxInFlightRequestsPerWorld,
                maxInputTokensPerMinute, encounterHeartbeatTicks, contingenciesPerMob, minimumConfidence,
                tacticSwitchMargin, maxInstallsPerTick, maxEncounterEvaluationsPerTick, maxProbesPerTick,
                maxCompletionsPerTick, maxSignalsPerTick, maxTickWorkMicros, maxDecisionAgeMillis,
                lowConfidenceStreakForLocal, searchRadiusBlocks, logDecisions, recordScenarios,
                QuestionSettings.standard());
    }

    /**
     * Default of {@code decision.minimumConfidence}: 0.45 since the re-bench of M16.10 (user decision of 2026-09-30,
     * DT-31: with {@code full}, the answers kept at 0.45 are 90.2 % acceptable, Wilson lower bound 0.775, none
     * forbidden). The only place of this default (C13).
     */
    public static final double DEFAULT_MINIMUM_CONFIDENCE = 0.45;

    /**
     * Returns the defaults of a fresh installation for the default Jev timeout.
     *
     * @return the default settings
     */
    public static DecisionSettings standard() {
        return new DecisionSettings(600, 300, 8, 4, 600_000, 20, 1, DEFAULT_MINIMUM_CONFIDENCE, 0.15, 10, 2, 64, 16,
                64, 2000,
                defaultMaxDecisionAgeMillis(JevSettings.standard().timeoutMillis()), 3, 48.0, false, false);
    }

    /**
     * Default of {@code decision.maxDecisionAgeMillis}: the Jev timeout plus 200 ms, capped at 5000 ms.
     *
     * @param timeoutMillis {@code jev.timeoutMillis}
     * @return the default maximum decision age in milliseconds
     */
    public static int defaultMaxDecisionAgeMillis(int timeoutMillis) {
        return Math.min(5000, timeoutMillis + 200);
    }

    /**
     * Reads the {@code decision} and {@code debug} sections. Absent keys keep {@link #standard()}, except
     * that the per-world defaults never exceed the global limits and the decision age follows the timeout.
     *
     * @param configuration root of {@code config.yml}
     * @param jev already validated Jev settings
     * @return the validated settings
     * @throws ConfigurationException if a value has the wrong type or range
     */
    public static DecisionSettings from(ConfigurationSection configuration, JevSettings jev) {
        return from(configuration, jev, ignored -> {
        });
    }

    /**
     * Reads the {@code decision} and {@code debug} sections and reports the obsolete ready-check cap (W5, M20.3).
     * The obsolete value is not the session cap: a shipped {@code 64} must not replace the default {@code 96}.
     *
     * @param configuration root of {@code config.yml}
     * @param jev already validated Jev settings
     * @param warnings receiver of obsolete-key warnings
     * @return the validated settings
     * @throws ConfigurationException if a value has the wrong type or range
     */
    public static DecisionSettings from(ConfigurationSection configuration, JevSettings jev,
                                        Consumer<ConfigProblem> warnings) {
        Objects.requireNonNull(jev, "jev");
        Objects.requireNonNull(warnings, "warnings");
        var standard = standard();
        if (configuration.contains("decision.maxReadyChecksPerTick")) {
            warnings.accept(ConfigProblem.warning(PluginConfigurationLoader.FILE, "decision.maxReadyChecksPerTick",
                    "is obsolete; the tick layer uses decision.tickLayer.maxSessionsPerTick"));
        }
        var perMinute = read(configuration, "maxRequestsPerMinute", standard.maxRequestsPerMinute());
        var inFlight = read(configuration, "maxInFlightRequests", standard.maxInFlightRequests());
        return new DecisionSettings(perMinute,
                read(configuration, "maxRequestsPerMinutePerWorld",
                        Math.min(standard.maxRequestsPerMinutePerWorld(), perMinute)),
                inFlight,
                read(configuration, "maxInFlightRequestsPerWorld",
                        Math.min(standard.maxInFlightRequestsPerWorld(), inFlight)),
                read(configuration, "maxInputTokensPerMinute", standard.maxInputTokensPerMinute()),
                read(configuration, "encounterHeartbeatTicks", standard.encounterHeartbeatTicks()),
                read(configuration, "contingenciesPerMob", standard.contingenciesPerMob()),
                ConfigurationValues.decimal(configuration, "decision.minimumConfidence", standard.minimumConfidence()),
                ConfigurationValues.decimal(configuration, "decision.tacticSwitchMargin", standard.tacticSwitchMargin()),
                read(configuration, "maxInstallsPerTick", standard.maxInstallsPerTick()),
                read(configuration, "maxEncounterEvaluationsPerTick", standard.maxEncounterEvaluationsPerTick()),
                read(configuration, "maxProbesPerTick", standard.maxProbesPerTick()),
                read(configuration, "maxCompletionsPerTick", standard.maxCompletionsPerTick()),
                read(configuration, "maxSignalsPerTick", standard.maxSignalsPerTick()),
                read(configuration, "maxTickWorkMicros", standard.maxTickWorkMicros()),
                read(configuration, "maxDecisionAgeMillis", defaultMaxDecisionAgeMillis(jev.timeoutMillis())),
                read(configuration, "lowConfidenceStreakForLocal", standard.lowConfidenceStreakForLocal()),
                ConfigurationValues.decimal(configuration, "decision.searchRadiusBlocks", standard.searchRadiusBlocks()),
                ConfigurationValues.booleanValue(configuration, "decision.logDecisions", standard.logDecisions()),
                ConfigurationValues.booleanValue(configuration, "debug.recordScenarios", standard.recordScenarios()),
                QuestionSettings.from(configuration),
                mode(configuration, "decision.castMode", CastMode.class, standard.castMode()),
                mode(configuration, "decision.castThread", CastThread.class, standard.castThread()),
                mode(configuration, "decision.untypedCast", UntypedCastMode.class, standard.untypedCast()),
                read(configuration, "readyHorizonTicks", standard.readyHorizonTicks()),
                mode(configuration, "decision.tickLayer.mode", TickLayerMode.class, standard.tickLayerMode()),
                read(configuration, "tickLayer.maxSessionsPerTick", standard.maxSessionsPerTick()),
                read(configuration, "tickLayer.maxMicros", standard.tickLayerMaxMicros()),
                read(configuration, "tickLayer.guardMspt", standard.guardMspt()),
                read(configuration, "tickLayer.microMinHoldTicks", standard.microMinHoldTicks()),
                read(configuration, "tickLayer.waypointRefreshTicks", standard.waypointRefreshTicks()),
                read(configuration, "tickLayer.maxWaypointPathsPerTick", standard.maxWaypointPathsPerTick()),
                read(configuration, "tickLayer.maxWaypointPathMicrosPerTick", standard.maxWaypointPathMicrosPerTick()),
                mode(configuration, "decision.candidateScoring", CandidateScoring.class, standard.candidateScoring()),
                ConfigurationValues.booleanValue(configuration, "decision.candidateDiversity",
                        standard.candidateDiversity()),
                ConfigurationValues.integer(configuration, "debug.seed", standard.debugSeed()),
                mode(configuration, "decision.pruning", PruningMode.class, standard.pruning()),
                LearningConfiguration.gate(configuration),
                LearningConfiguration.minimum(configuration));
    }

    static <E extends Enum<E>> E mode(ConfigurationSection configuration, String path, Class<E> type, E fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        var text = ConfigurationValues.token(configuration, path, "").trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("OFF".equals(text) && type == TickLayerMode.class) {
            throw new ConfigurationException(ConfigurationValues.path(configuration, path), "removed; use casts");
        }
        for (var value : type.getEnumConstants()) {
            if (value.name().equals(text)) {
                return value;
            }
        }
        throw new ConfigurationException(ConfigurationValues.path(configuration, path),
                "must be " + ModeNames.of(type));
    }

    private static int read(ConfigurationSection configuration, String key, int fallback) {
        return ConfigurationValues.intValue(configuration, "decision." + key, fallback);
    }
}
