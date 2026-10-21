package akyrion.thepredict.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * Loads {@code AIConfig/config.yml} of schema 2 strictly and detects schema 1 files (M03.1, M03.6). A broken file
 * never turns into active defaults: every error names {@code AIConfig/config.yml} and the full path.
 */
public final class PluginConfigurationLoader {
    /** Path cited by problems of the master file. */
    public static final String FILE = ConfigLocations.masterLabel();
    /** Schema version this release reads. */
    public static final int SCHEMA_VERSION = 2;
    /** Every path accepted in a schema 2 {@code config.yml}. */
    public static final Set<String> ALLOWED_PATHS = Set.of("schemaVersion",
            "jev", "jev.baseUrl", "jev.model", "jev.apiKeyEnvironment", "jev.apiKey", "jev.timeoutMillis",
            "jev.connectTimeoutMillis", "jev.maxResponseBytes", "jev.maxStateChars",
            "decision", "decision.maxRequestsPerMinute", "decision.maxRequestsPerMinutePerWorld",
            "decision.maxInFlightRequests", "decision.maxInFlightRequestsPerWorld", "decision.maxInputTokensPerMinute",
            "decision.encounterHeartbeatTicks", "decision.contingenciesPerMob", "decision.minimumConfidence",
            "decision.tacticSwitchMargin", "decision.maxInstallsPerTick", "decision.maxEncounterEvaluationsPerTick",
            "decision.maxProbesPerTick", "decision.maxCompletionsPerTick", "decision.maxSignalsPerTick",
            "decision.maxTickWorkMicros", "decision.maxDecisionAgeMillis", "decision.lowConfidenceStreakForLocal",
            "decision.searchRadiusBlocks", "decision.logDecisions", "decision.maxOptionsPerQuestion",
            "decision.questionDetail", "decision.fitChecks", "decision.fitChecksAllowed", "decision.fitVeto",
            "decision.fitRescue", "decision.castMode", "decision.castThread", "decision.untypedCast",
            "decision.readyHorizonTicks", "decision.maxReadyChecksPerTick", "decision.pruning",
            "decision.tickLayer", "decision.tickLayer.mode", "decision.tickLayer.maxSessionsPerTick",
            "decision.tickLayer.maxMicros", "decision.tickLayer.guardMspt", "decision.tickLayer.microMinHoldTicks",
            "decision.tickLayer.waypointRefreshTicks", "decision.tickLayer.maxWaypointPathsPerTick",
            "decision.tickLayer.maxWaypointPathMicrosPerTick", "decision.candidateScoring",
            "decision.confidenceGate", "decision.minimumCalibratedConfidence",
            "decision.candidateDiversity",
            "decision.answerCache", "decision.answerCacheTtlTicks", "decision.answerCacheEntries",
            "decision.answerCacheRevalidateRatio",
            "records", "records.level", "records.bodies", "records.directory", "records.queueCapacity",
            "records.segmentMegabytes", "records.segmentMinutes", "records.compressClosedSegments",
            "records.retentionDays", "records.maxTotalMegabytes", "records.patternSummaryBinary",
            "storage", "storage.aggregates", "storage.file", "storage.synchronous", "storage.busyTimeoutMillis",
            "storage.flushIntervalTicks", "storage.maxPendingKeys", "storage.backupEveryHours", "storage.backupsKept",
            "debug", "debug.recordScenarios", "debug.seed",
            "participants", "participants.playerGameModes", "participants.entities", "participants.entities.allow",
            "participants.entities.deny", "participants.entities.profilesMayAllow",
            "participants.entities.perceiveNonTargets", "participants.entities.maxCandidatesPerMember",
            "profileDefaults", "profileDefaults.observationIntervalTicks",
            "profileDefaults.minimumDecisionIntervalTicks", "profileDefaults.maximumIntentDurationTicks",
            "profileDefaults.criticalHealthRatio", "profileDefaults.woundedHealthRatio",
            "profileDefaults.meleeReachBlocks", "profileDefaults.bandInnerBlocks", "profileDefaults.bandOuterBlocks",
            "profileDefaults.rangedReachBlocks", "profileDefaults.retreatDistanceBlocks",
            "profileDefaults.retreatSpeed", "profileDefaults.approachSpeed",
            "difficulty", "difficulty.defaultMode", "difficulty.defaultPreset", "difficulty.adjustIntervalTicks",
            "difficulty.toleranceRatio", "difficulty.deadbandRatio", "difficulty.learningGain",
            "difficulty.projectedTtkCapRatio", "difficulty.dampingReversals", "difficulty.estimatorHalfLifeTicks",
            "difficulty.minEvidenceTicks", "difficulty.disengageTicks", "difficulty.maxTrackedFights",
            "difficulty.nativeLevelAllowed", "difficulty.presets", "difficulty.worlds",
            "difficulty.presets.facil", "difficulty.presets.facil.targetMinutes",
            "difficulty.presets.facil.reactionFloorTicks", "difficulty.presets.facil.entropyReactionFloorTicks",
            "difficulty.presets.facil.entropyAdaptStartSeconds",
            "difficulty.presets.normal", "difficulty.presets.normal.targetMinutes",
            "difficulty.presets.normal.reactionFloorTicks", "difficulty.presets.normal.entropyReactionFloorTicks",
            "difficulty.presets.normal.entropyAdaptStartSeconds",
            "difficulty.presets.dificil", "difficulty.presets.dificil.targetMinutes",
            "difficulty.presets.dificil.reactionFloorTicks", "difficulty.presets.dificil.entropyReactionFloorTicks",
            "difficulty.presets.dificil.entropyAdaptStartSeconds",
            "difficulty.presets.caos", "difficulty.presets.caos.targetMinutes",
            "difficulty.presets.caos.reactionFloorTicks", "difficulty.presets.caos.entropyReactionFloorTicks",
            "difficulty.presets.caos.entropyAdaptStartSeconds",
            "difficulty.presets.vacio", "difficulty.presets.vacio.targetMinutes",
            "difficulty.presets.vacio.reactionFloorTicks", "difficulty.presets.vacio.entropyReactionFloorTicks",
            "difficulty.presets.vacio.entropyAdaptStartSeconds",
            "telegraph", "telegraph.againstPlayers", "telegraph.againstEntities", "telegraph.minTicks",
            "telegraph.minTicks.light", "telegraph.minTicks.medium", "telegraph.minTicks.heavy",
            "telegraph.minTicks.severe", "telegraph.answerTicks", "telegraph.answerTicks.block",
            "telegraph.answerTicks.dodge", "telegraph.answerTicks.backOff", "telegraph.answerTicks.breakSight",
            "telegraph.answerTicks.interrupt", "telegraph.deliveryMarginTicks", "telegraph.maxReleasesPerTick",
            "telegraph.whiffRecoveryTicks", "telegraph.whiffCooldownTicks", "telegraph.resultWindowTicks",
            "stance", "stance.nativeMode", "stance.minDwellTicks", "stance.hysteresisCycles",
            "zone", "zone.mode", "zone.defaultRadiusBlocks", "zone.maxRadiusBlocks", "zone.bandBlocks",
            "zone.maxZones", "zone.maxSnapshotsPerTick", "zone.maxConcurrentBuilds", "zone.maxDropScanBlocks",
            "zone.maxMicrosPerTick", "zone.maxColumnReadsPerTick", "zone.minColumnRefreshTicks",
            "zone.maxDirtyQueue", "zone.dirtyRebuildThreshold", "zone.verifyIntervalTicks", "zone.idleDropTicks",
            "zone.maxQueriesPerTick", "zone.knockbackDepthBlocks", "zone.strongPushDepthBlocks",
            "zone.maxSafeDropBlocks", "zone.factPeriodTicks", "zone.factOnTicks", "zone.factOffTicks",
            "zone.maxFactsPerMob", "zone.pushDeferTicks", "zone.pushDeferMaxTicks", "zone.pushAttributionTicks",
            "zone.answerRetryTicks", "zone.answerMaxWaitTicks",
            "group", "group.mode", "group.maxSquads", "group.brokenTicks", "group.arbitrationPeriodTicks",
            "group.replanPeriodTicks", "group.maxReplansPerTick", "group.maxMicrosPerTick",
            "group.maxMeleeAttackersPerPlayer", "group.maxRangedAttackersPerPlayer", "group.minImpactGapTicks",
            "group.tokenIdleTimeoutTicks", "group.stealMarginMillis", "group.idleWarnTicks",
            "group.roleSwitchMarginMillis", "group.exitSectorDegrees", "group.lkpForgetTicks",
            "group.maxMediumTellsPerPlayer", "group.bigImpactSpacingTicks", "group.leaderReserveTicks",
            "group.projectileFlightMarginTicks", "group.maxPlanCandidates", "group.minPlanTicks",
            "group.planHorizonTicks", "group.assignmentThreatMargin", "group.factOnTicks", "group.factOffTicks",
            "group.maxFacts",
            "patterns", "patterns.mode", "patterns.hearingBlocks", "patterns.sampleTicks",
            "patterns.maxWatchedPerEncounter", "patterns.maxReadsPerTick", "patterns.strafeBlocks",
            "patterns.airSwings", "patterns.mergeTicks", "patterns.idleTicks", "patterns.maxTokensPerSecond",
            "patterns.order2Contexts", "patterns.forgetting", "patterns.evidenceForgetting",
            "patterns.confidenceQuantile", "patterns.minTrials", "patterns.persistUpdates", "patterns.exitMargin",
            "patterns.changepointH", "patterns.forecastMinAccuracy", "patterns.histogramBinTicks",
            "patterns.maxMicrosPerTick", "patterns.maxExploitShare", "patterns.maxCounterDelayTicks",
            "patterns.exploitWindowTicks", "patterns.restAfterSuccesses", "patterns.restTicks",
            "patterns.maxExploitedPatterns", "patterns.factOnTicks", "patterns.factOffTicks",
            "patterns.maxFactsPerParticipant", "patterns.maxFactsPerRequest",
            "memory", "memory.mode", "memory.fightDecay", "memory.halfLifeDays", "memory.crossFightH",
            "memory.styleBreakFactor", "memory.seedMaxTrials", "memory.minFightTrials", "memory.rungPriorFights",
            "memory.maxRungOffset", "memory.minDamageShareRatio", "memory.maxProfilesPerPlayer",
            "memory.maxFactsPerRequest", "memory.factMaxAgeDays", "memory.cacheSubjects", "memory.maxHintsPerTick",
            "memory.loadTimeoutSeconds", "memory.refreshSeconds", "memory.maxPendingDigestsPerSubject",
            "memory.maxPendingSubjects", "memory.maxSubjectsPerBatch", "memory.retentionDays", "memory.maxRows",
            "memory.backend", "learning", "learning.allowUnverified", "learning.collect", "learning.scope",
            "learning.baseScoring", "learning.trainingSources", "learning.contextVersion",
            "learning.maxContextKeysPerProfile", "learning.maxOpenWindowsPerSession", "learning.creditShare",
            "learning.groupCreditShare", "learning.creditDecayPerWindow", "learning.minWindowsForValue",
            "learning.halfLifeDays", "learning.weeksKept", "learning.maxEntries", "learning.minValueSpreadMillis",
            "learning.buildChunkRows", "learning.exploreInDuels", "learning.exploration",
            "learning.exploration.policy", "learning.exploration.epsilon", "learning.exploration.maxShare",
            "learning.calibration", "learning.calibration.minDecisions", "learning.calibration.priorStrength",
            "learning.calibration.driftReferenceWindows", "learning.voiSkip", "learning.voi",
            "learning.voi.minWindows", "learning.voi.minAgreement", "learning.voi.maxExpectedGain",
            "learning.voi.maxConsecutiveSkips", "learning.jevHint", "learning.groupPlans",
            "jev.provider",
            "distill", "distill.mode", "distill.scope", "distill.collect", "distill.minCellOffers",
            "distill.maxTreeDepth", "distill.maxLeaves", "distill.minLeafDecisions", "distill.overrideZ",
            "distill.minCellDecisions", "distill.nonInferiorityMargin", "distill.coverageRefreshMinutes",
            "distill.coverageMaxAgeMinutes", "distill.auditEvery", "distill.assistMinDelegable",
            "distill.autonomous", "distill.autonomous.jevForOod", "distill.regime", "distill.regime.openingSeconds",
            "distill.regime.dwellTicks", "distill.regime.maxSwitchesPerEncounter", "distill.regime.minGain",
            "distill.regime.minDecisions", "distill.regime.extraDimensions", "distill.maxRegimesPerProfile",
            "distill.maxArtifactBytes", "distill.weeksKept", "distill.drift", "distill.drift.cusumH",
            "autoconfig", "autoconfig.initialConfigs", "autoconfig.initialPairs", "autoconfig.eta",
            "autoconfig.maxPairs", "autoconfig.maxMinutes", "autoconfig.autoApply");
    /** Paths that exist only in schema 1 and identify a file that needs migration. */
    public static final Set<String> VERSION_ONE_PATHS = Set.of("provider", "provider.type", "provider.model",
            "provider.timeoutMillis", "provider.apiKeyEnvironment", "provider.apiKey",
            "decision.maxConcurrentRequests", "decision.maxConcurrentRequestsPerWorld",
            "decision.minimumSecondsBetweenRequestsPerMob", "decision.minimumSecondsAfterResponsePerMob",
            "decision.maxMobEvaluationsPerPoll", "decision.maxDecisionAgeTicks",
            "profileDefaults.decisionIntervalTicks", "profileDefaults.lowHealthRatio",
            "profileDefaults.attackRangeBlocks");
    private static final Set<String> SECTIONS = sections();

    private static Set<String> sections() {
        var sections = new java.util.LinkedHashSet<String>();
        sections.addAll(Set.of("jev", "decision", "debug", "profileDefaults", "participants", "records", "storage",
                "telegraph", "telegraph.minTicks", "telegraph.answerTicks", "stance", "zone", "group",
                "patterns", "memory", "learning", "learning.exploration", "learning.calibration", "learning.voi",
                "distill", "distill.autonomous", "distill.regime", "distill.drift", "autoconfig"));
        sections.addAll(DifficultyConfiguration.sections());
        sections.remove("difficulty.worlds");
        return Set.copyOf(sections);
    }

    /** Creates the strict plugin configuration loader. */
    public PluginConfigurationLoader() {
    }

    /**
     * Reads the YAML syntax of a file without interpreting it.
     *
     * @param path plugin {@code config.yml}
     * @return the parsed YAML
     * @throws IOException when the file cannot be read
     * @throws InvalidConfigurationException when the YAML syntax is invalid
     */
    public YamlConfiguration read(Path path) throws IOException, InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        configuration.load(path.toFile());
        return configuration;
    }

    /**
     * Tells whether a file is a schema 1 configuration: no {@code schemaVersion} and at least one field
     * that exists only in schema 1, such as {@code provider}.
     *
     * @param configuration parsed YAML
     * @return {@code true} for a file that needs migration
     */
    public boolean isVersionOne(ConfigurationSection configuration) {
        return !configuration.contains("schemaVersion")
                && configuration.getKeys(true).stream().anyMatch(VERSION_ONE_PATHS::contains);
    }

    /**
     * Interprets a schema 2 file.
     *
     * @param configuration parsed YAML
     * @return the validated settings
     * @throws ConfigurationException if the schema, a field, a type, a range or a cross check is invalid
     */
    public PluginSettings parse(ConfigurationSection configuration) {
        requireSchemaVersion(configuration);
        for (var path : new TreeSet<>(configuration.getKeys(true))) {
            if (VERSION_ONE_PATHS.contains(path)) {
                throw new ConfigurationException(path, "is a schema 1 field; review the migration proposal under migration/");
            }
            if (!ALLOWED_PATHS.contains(path) && !DifficultyConfiguration.worldPath(path)) {
                throw new ConfigurationException(path, "is not a known field");
            }
        }
        for (var section : SECTIONS) {
            ConfigurationValues.section(configuration, section);
        }
        var jev = JevSettings.from(configuration);
        return new PluginSettings(jev, DecisionSettings.from(configuration, jev), ProfileDefaults.from(configuration),
                ParticipantSettings.from(configuration), StorageSettings.from(configuration),
                DifficultyConfiguration.parse(configuration), TelegraphConfiguration.telegraph(configuration),
                TelegraphConfiguration.stance(configuration), ZoneConfiguration.zone(configuration),
                GroupConfiguration.group(configuration), PatternConfiguration.patterns(configuration),
                MemoryConfiguration.memory(configuration), LearningConfiguration.learning(configuration),
                DistillConfiguration.distill(configuration), AutoconfigConfiguration.autoconfig(configuration));
    }

    /**
     * Interprets a schema 2 file and checks the key the process resolves against {@code jev.model}, so a key pasted
     * into the model field blocks activation at load with file and path instead of at the first request (W6).
     *
     * @param configuration parsed YAML
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @return the validated settings
     * @throws ConfigurationException if the schema, a field, a type, a range, a cross check or the key is invalid
     */
    public PluginSettings parse(ConfigurationSection configuration, UnaryOperator<String> environment) {
        var settings = parse(configuration);
        var problem = settings.jev().keyProblem(Objects.requireNonNull(environment, "environment"));
        if (problem.isPresent()) {
            throw problem.get();
        }
        return settings;
    }

    /**
     * Reads and interprets a schema 2 file.
     *
     * @param path plugin {@code config.yml}
     * @return the validated settings
     * @throws IOException when the file cannot be read
     * @throws InvalidConfigurationException when the YAML syntax is invalid
     * @throws ConfigurationException if the file is schema 1 or any value is invalid
     */
    public PluginSettings load(Path path) throws IOException, InvalidConfigurationException {
        var configuration = read(path);
        if (isVersionOne(configuration)) {
            throw new ConfigurationException("", "uses schema 1; review the migration proposal under migration/");
        }
        return parse(configuration);
    }

    private static void requireSchemaVersion(ConfigurationSection configuration) {
        if (!configuration.contains("schemaVersion")) {
            throw new ConfigurationException("schemaVersion", "is required and must be " + SCHEMA_VERSION);
        }
        if (!(configuration.get("schemaVersion") instanceof Integer version) || version != SCHEMA_VERSION) {
            throw new ConfigurationException("schemaVersion", "must be " + SCHEMA_VERSION + ", was "
                    + ConfigurationValues.safeValue(configuration.get("schemaVersion")));
        }
    }
}
