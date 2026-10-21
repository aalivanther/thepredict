package akyrion.thepredict.config;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.ReactionBands;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TickLayerMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginSettingsTest {
    @Test
    void heartbeatMustNotBeShorterThanTheMinimumDecisionInterval() {
        var error = assertThrows(ConfigurationException.class,
                () -> new PluginSettings(JevSettings.standard(), decision(19, 1000), defaults(20, 40),
                ParticipantSettings.standard()));
        assertEquals("decision.encounterHeartbeatTicks", error.path());
        assertDoesNotThrow(() -> new PluginSettings(JevSettings.standard(), decision(20, 1000), defaults(20, 40),
                ParticipantSettings.standard()));
    }

    @Test
    void timeoutMustEndBeforeTheIntentExpires() {
        var error = assertThrows(ConfigurationException.class,
                () -> new PluginSettings(jev(1000), decision(20, 1000), defaults(20, 20),
                ParticipantSettings.standard()));
        assertEquals("jev.timeoutMillis", error.path());
        assertEquals("must be below profileDefaults.maximumIntentDurationTicks x 50 (1000 ms), was 1000", error.reason());
        assertDoesNotThrow(() -> new PluginSettings(jev(999), decision(20, 1000), defaults(20, 20),
                ParticipantSettings.standard()));
    }

    @Test
    void decisionAgeMustCoverTheTimeout() {
        var error = assertThrows(ConfigurationException.class,
                () -> new PluginSettings(jev(900), decision(20, 899), defaults(20, 40),
                ParticipantSettings.standard()));
        assertEquals("decision.maxDecisionAgeMillis", error.path());
        assertDoesNotThrow(() -> new PluginSettings(jev(900), decision(20, 900), defaults(20, 40),
                ParticipantSettings.standard()));
    }

    @Test
    void timingUsesGlobalPacingAndProfileThresholds() {
        var settings = PluginSettings.standard();

        assertEquals(delivered(0.45, 0.15), settings.timing());
        assertEquals(delivered(0.7, 0.05), settings.timing(0.7, 0.05));
    }

    private static DecisionTiming delivered(double confidence, double margin) {
        return new DecisionTiming(20, 20, 40, confidence, margin, 1).withCasting(CastMode.DEFAULT, 0)
                .withLayer(TickLayerMode.DEFAULT, ReactionScope.DEFAULT, ReactionBands.DEFAULT_AHEAD, 0,
                        DecisionCadence.DEFAULT, 6, 10);
    }

    private static JevSettings jev(int timeoutMillis) {
        var standard = JevSettings.standard();
        return new JevSettings(standard.baseUrl(), standard.model(), standard.apiKeyEnvironment(), "",
                Duration.ofMillis(timeoutMillis), standard.connectTimeout(), standard.maxResponseBytes(),
                standard.maxStateChars());
    }

    private static DecisionSettings decision(int heartbeatTicks, int maxDecisionAgeMillis) {
        var s = DecisionSettings.standard();
        return new DecisionSettings(s.maxRequestsPerMinute(), s.maxRequestsPerMinutePerWorld(), s.maxInFlightRequests(),
                s.maxInFlightRequestsPerWorld(), s.maxInputTokensPerMinute(), heartbeatTicks, s.contingenciesPerMob(),
                s.minimumConfidence(), s.tacticSwitchMargin(), s.maxInstallsPerTick(), s.maxEncounterEvaluationsPerTick(),
                s.maxProbesPerTick(), s.maxCompletionsPerTick(), s.maxSignalsPerTick(), s.maxTickWorkMicros(),
                maxDecisionAgeMillis, s.lowConfidenceStreakForLocal(), s.searchRadiusBlocks(), s.logDecisions(),
                s.recordScenarios());
    }

    private static ProfileDefaults defaults(int minimumDecisionIntervalTicks, int maximumIntentDurationTicks) {
        var s = ProfileDefaults.standard();
        return new ProfileDefaults(s.observationIntervalTicks(), minimumDecisionIntervalTicks, maximumIntentDurationTicks,
                s.criticalHealthRatio(), s.woundedHealthRatio(), s.meleeReachBlocks(), s.bandInnerBlocks(),
                s.bandOuterBlocks(), s.rangedReachBlocks(), s.retreatDistanceBlocks(), s.retreatSpeed(), s.approachSpeed());
    }
}
