package akyrion.thepredict.config;

import java.util.List;
import java.util.StringJoiner;

/**
 * Writes a schema 2 {@code config.yml} for a migration proposal. The key is always written empty, so a
 * proposal can never enable paid queries (M03.6, W6).
 */
final class ProposedConfiguration {
    private ProposedConfiguration() {
    }

    /**
     * Writes the keys a schema 1 file can carry over, in the order of the bundled file: {@code jev}, the budget,
     * question and fit keys of {@code decision} (not {@code maxDecisionAgeMillis}, left to its default derived from
     * {@code jev.timeoutMillis}, nor the later {@code decision} keys), {@code debug.recordScenarios},
     * {@code profileDefaults} and {@code participants}. Every other key keeps its default, which the loader supplies.
     */
    static String write(PluginSettings settings) {
        var text = new StringBuilder("schemaVersion: ").append(PluginConfigurationLoader.SCHEMA_VERSION).append('\n');
        writeJev(text, settings.jev());
        writeDecision(text, settings.decision());
        writeProfileDefaults(text, settings.profileDefaults());
        writeParticipants(text, settings.participants());
        return text.toString();
    }

    private static void writeJev(StringBuilder text, JevSettings jev) {
        text.append("jev:\n");
        line(text, "baseUrl", YamlText.quote(jev.baseUrl().toString()));
        line(text, "model", YamlText.quote(jev.model()));
        line(text, "apiKeyEnvironment", YamlText.quote(jev.apiKeyEnvironment()));
        line(text, "apiKey", "''");
        line(text, "timeoutMillis", jev.timeoutMillis());
        line(text, "connectTimeoutMillis", jev.connectTimeout().toMillis());
        line(text, "maxResponseBytes", jev.maxResponseBytes());
        line(text, "maxStateChars", jev.maxStateChars());
    }

    private static void writeDecision(StringBuilder text, DecisionSettings decision) {
        text.append("decision:\n");
        line(text, "maxRequestsPerMinute", decision.maxRequestsPerMinute());
        line(text, "maxRequestsPerMinutePerWorld", decision.maxRequestsPerMinutePerWorld());
        line(text, "maxInFlightRequests", decision.maxInFlightRequests());
        line(text, "maxInFlightRequestsPerWorld", decision.maxInFlightRequestsPerWorld());
        line(text, "maxInputTokensPerMinute", decision.maxInputTokensPerMinute());
        line(text, "encounterHeartbeatTicks", decision.encounterHeartbeatTicks());
        line(text, "contingenciesPerMob", decision.contingenciesPerMob());
        line(text, "minimumConfidence", YamlText.number(decision.minimumConfidence()));
        line(text, "tacticSwitchMargin", YamlText.number(decision.tacticSwitchMargin()));
        line(text, "maxInstallsPerTick", decision.maxInstallsPerTick());
        line(text, "maxEncounterEvaluationsPerTick", decision.maxEncounterEvaluationsPerTick());
        line(text, "maxProbesPerTick", decision.maxProbesPerTick());
        line(text, "maxCompletionsPerTick", decision.maxCompletionsPerTick());
        line(text, "maxSignalsPerTick", decision.maxSignalsPerTick());
        line(text, "maxTickWorkMicros", decision.maxTickWorkMicros());
        line(text, "lowConfidenceStreakForLocal", decision.lowConfidenceStreakForLocal());
        line(text, "searchRadiusBlocks", YamlText.number(decision.searchRadiusBlocks()));
        line(text, "logDecisions", decision.logDecisions());
        line(text, "maxOptionsPerQuestion", decision.questions().maxOptionsPerQuestion());
        line(text, "questionDetail", QuestionSettings.text(decision.questions().questionDetail()));
        line(text, "fitChecks", decision.questions().fitChecks());
        line(text, "fitChecksAllowed", decision.questions().fitChecksAllowed());
        line(text, "fitVeto", YamlText.number(decision.questions().fitVeto()));
        line(text, "fitRescue", YamlText.number(decision.questions().fitRescue()));
        text.append("debug:\n");
        line(text, "recordScenarios", decision.recordScenarios());
    }

    private static void writeProfileDefaults(StringBuilder text, ProfileDefaults defaults) {
        text.append("profileDefaults:\n");
        line(text, "observationIntervalTicks", defaults.observationIntervalTicks());
        line(text, "minimumDecisionIntervalTicks", defaults.minimumDecisionIntervalTicks());
        line(text, "maximumIntentDurationTicks", defaults.maximumIntentDurationTicks());
        line(text, "criticalHealthRatio", YamlText.number(defaults.criticalHealthRatio()));
        line(text, "woundedHealthRatio", YamlText.number(defaults.woundedHealthRatio()));
        line(text, "meleeReachBlocks", YamlText.number(defaults.meleeReachBlocks()));
        line(text, "bandInnerBlocks", YamlText.number(defaults.bandInnerBlocks()));
        line(text, "bandOuterBlocks", YamlText.number(defaults.bandOuterBlocks()));
        line(text, "rangedReachBlocks", YamlText.number(defaults.rangedReachBlocks()));
        line(text, "retreatDistanceBlocks", YamlText.number(defaults.retreatDistanceBlocks()));
        line(text, "retreatSpeed", YamlText.number(defaults.retreatSpeed()));
        line(text, "approachSpeed", YamlText.number(defaults.approachSpeed()));
    }

    private static void writeParticipants(StringBuilder text, ParticipantSettings participants) {
        var rules = participants.global();
        text.append("participants:\n");
        line(text, "playerGameModes", list(List.copyOf(rules.playerGameModes())));
        text.append("  entities:\n");
        line(text, "  allow", list(rules.entityAllow()));
        line(text, "  deny", list(rules.entityDeny()));
        line(text, "  profilesMayAllow", participants.profilesMayAllow());
        line(text, "  perceiveNonTargets", rules.perceiveNonTargets());
        line(text, "  maxCandidatesPerMember", participants.maxCandidatesPerMember());
    }

    private static String list(List<String> values) {
        var joined = new StringJoiner(", ", "[", "]");
        values.forEach(value -> joined.add(YamlText.quote(value)));
        return joined.toString();
    }

    private static void line(StringBuilder text, String key, Object value) {
        text.append("  ").append(key).append(": ").append(value).append('\n');
    }
}
