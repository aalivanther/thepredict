package akyrion.thepredict.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.TreeSet;

/**
 * Converts a schema 1 {@code config.yml} into schema 2 settings (M03.6). The OpenRouter key is never
 * copied; limits with an equivalent are carried when they fit the new ranges, the rest are listed.
 */
final class ConfigurationMigration {
    private static final String FILE = PluginConfigurationLoader.FILE;
    private static final Map<String, String> WITHOUT_EQUIVALENT = Map.of(
            "decision.minimumSecondsBetweenRequestsPerMob", "schema 2 paces decisions per encounter with "
                    + "decision.encounterHeartbeatTicks and profileDefaults.minimumDecisionIntervalTicks",
            "decision.minimumSecondsAfterResponsePerMob", "an adopted intent now lasts "
                    + "profileDefaults.maximumIntentDurationTicks",
            "decision.maxMobEvaluationsPerPoll", "work per tick is bounded by decision.maxEncounterEvaluationsPerTick "
                    + "and decision.maxProbesPerTick",
            "decision.maxDecisionAgeTicks", "the age limit is decision.maxDecisionAgeMillis, derived from "
                    + "jev.timeoutMillis",
            "profileDefaults.attackRangeBlocks", "attacking_phase now uses the melee band "
                    + "(profileDefaults.meleeReachBlocks)");

    private ConfigurationMigration() {
    }

    /** Builds the proposed settings and records every decision. */
    static PluginSettings convert(ConfigurationSection v1, MigrationNotes notes) {
        describeProvider(v1, notes);
        WITHOUT_EQUIVALENT.keySet().stream().sorted().filter(v1::contains).forEach(path -> notes.decision(FILE + ": "
                + path + " has no equivalent and was dropped: " + WITHOUT_EQUIVALENT.get(path)));
        for (var path : new TreeSet<>(v1.getKeys(true))) {
            if (!PluginConfigurationLoader.VERSION_ONE_PATHS.contains(path)
                    && !PluginConfigurationLoader.ALLOWED_PATHS.contains(path)) {
                notes.manual(FILE + ": unknown field " + path + " was ignored");
            }
        }
        var decision = decision(v1, notes);
        var defaults = profileDefaults(v1, notes);
        try {
            return new PluginSettings(JevSettings.standard(), decision, defaults, ParticipantSettings.standard());
        } catch (ConfigurationException exception) {
            notes.manual(FILE + ": the carried values fail a schema 2 check (" + exception.getMessage()
                    + "); the proposal keeps the schema 2 defaults for decision and profileDefaults");
            return PluginSettings.standard();
        }
    }

    private static void describeProvider(ConfigurationSection v1, MigrationNotes notes) {
        notes.decision(FILE + ": provider was replaced by jev with model " + JevSettings.DEFAULT_MODEL
                + ", apiKeyEnvironment " + JevSettings.DEFAULT_API_KEY_ENVIRONMENT + " and an empty apiKey");
        if (v1.get("provider.apiKey") instanceof String key && !key.isBlank()) {
            notes.decision(FILE + ": provider.apiKey was not copied: an OpenRouter key is not a TypeSafe key; set "
                    + JevSettings.DEFAULT_API_KEY_ENVIRONMENT + " in the server environment");
        }
        if (v1.contains("provider.model")) {
            notes.decision(FILE + ": provider.model was dropped; Jev uses jev.model " + JevSettings.DEFAULT_MODEL);
        }
        if (v1.contains("provider.timeoutMillis")) {
            notes.decision(FILE + ": provider.timeoutMillis was dropped; jev.timeoutMillis uses "
                    + JevSettings.standard().timeoutMillis() + " ms because a slower answer describes a fight that changed");
        }
    }

    private static DecisionSettings decision(ConfigurationSection v1, MigrationNotes notes) {
        var standard = DecisionSettings.standard();
        var perMinute = carry(v1, "decision.maxRequestsPerMinute", "decision.maxRequestsPerMinute", 1, 1200, notes)
                .orElse(standard.maxRequestsPerMinute());
        var perWorld = carry(v1, "decision.maxRequestsPerMinutePerWorld", "decision.maxRequestsPerMinutePerWorld", 1,
                perMinute, notes).orElse(Math.min(standard.maxRequestsPerMinutePerWorld(), perMinute));
        var inFlight = carry(v1, "decision.maxConcurrentRequests", "decision.maxInFlightRequests", 1, 64, notes)
                .orElse(standard.maxInFlightRequests());
        var inFlightPerWorld = carry(v1, "decision.maxConcurrentRequestsPerWorld", "decision.maxInFlightRequestsPerWorld",
                1, inFlight, notes).orElse(Math.min(standard.maxInFlightRequestsPerWorld(), inFlight));
        var radius = carryDecimal(v1, "decision.searchRadiusBlocks", "decision.searchRadiusBlocks", 8.0, 128.0, notes)
                .orElse(standard.searchRadiusBlocks());
        var log = standard.logDecisions();
        if (v1.get("decision.logDecisions") instanceof Boolean value) {
            log = value;
            notes.decision(FILE + ": decision.logDecisions " + value + " was carried");
        }
        return new DecisionSettings(perMinute, perWorld, inFlight, inFlightPerWorld, standard.maxInputTokensPerMinute(),
                standard.encounterHeartbeatTicks(), standard.contingenciesPerMob(), standard.minimumConfidence(),
                standard.tacticSwitchMargin(), standard.maxInstallsPerTick(), standard.maxEncounterEvaluationsPerTick(),
                standard.maxProbesPerTick(), standard.maxCompletionsPerTick(), standard.maxSignalsPerTick(),
                standard.maxTickWorkMicros(), standard.maxDecisionAgeMillis(), standard.lowConfidenceStreakForLocal(),
                radius, log, standard.recordScenarios());
    }

    private static ProfileDefaults profileDefaults(ConfigurationSection v1, MigrationNotes notes) {
        var standard = ProfileDefaults.standard();
        var interval = carry(v1, "profileDefaults.decisionIntervalTicks", "profileDefaults.minimumDecisionIntervalTicks",
                10, DecisionSettings.standard().encounterHeartbeatTicks(), notes)
                .orElse(standard.minimumDecisionIntervalTicks());
        var critical = carryDecimal(v1, "profileDefaults.lowHealthRatio", "profileDefaults.criticalHealthRatio", 0.01,
                standard.woundedHealthRatio() - 0.01, notes).orElse(standard.criticalHealthRatio());
        return new ProfileDefaults(standard.observationIntervalTicks(), interval, standard.maximumIntentDurationTicks(),
                critical, standard.woundedHealthRatio(), standard.meleeReachBlocks(), standard.bandInnerBlocks(),
                standard.bandOuterBlocks(), standard.rangedReachBlocks(), standard.retreatDistanceBlocks(),
                standard.retreatSpeed(), standard.approachSpeed());
    }

    private static OptionalInt carry(ConfigurationSection v1, String from, String to, int minimum, int maximum,
                                     MigrationNotes notes) {
        if (!v1.contains(from)) {
            return OptionalInt.empty();
        }
        if (v1.get(from) instanceof Integer value && value >= minimum && value <= maximum) {
            notes.decision(FILE + ": " + from + " " + value + " was carried to " + to);
            return OptionalInt.of(value);
        }
        notes.manual(FILE + ": " + from + " (" + ConfigurationValues.safeValue(v1.get(from)) + ") does not fit " + to
                + " (" + minimum + " to " + maximum + "); the schema 2 default is proposed");
        return OptionalInt.empty();
    }

    private static OptionalDouble carryDecimal(ConfigurationSection v1, String from, String to, double minimum,
                                               double maximum, MigrationNotes notes) {
        if (!v1.contains(from)) {
            return OptionalDouble.empty();
        }
        if (v1.get(from) instanceof Number value && value.doubleValue() >= minimum && value.doubleValue() <= maximum) {
            notes.decision(FILE + ": " + from + " " + value + " was carried to " + to);
            return OptionalDouble.of(value.doubleValue());
        }
        notes.manual(FILE + ": " + from + " (" + ConfigurationValues.safeValue(v1.get(from)) + ") does not fit " + to
                + " (" + minimum + " to " + maximum + "); the schema 2 default is proposed");
        return OptionalDouble.empty();
    }
}
