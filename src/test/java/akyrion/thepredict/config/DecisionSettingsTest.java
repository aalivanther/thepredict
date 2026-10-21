package akyrion.thepredict.config;

import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.FitGate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionSettingsTest {
    private static final Map<String, int[]> INTEGER_RANGES = Map.ofEntries(
            Map.entry("maxRequestsPerMinute", new int[] {1, 1200}),
            Map.entry("maxInFlightRequests", new int[] {1, 64}),
            Map.entry("maxInputTokensPerMinute", new int[] {1000, 10_000_000}),
            Map.entry("encounterHeartbeatTicks", new int[] {10, 200}),
            Map.entry("contingenciesPerMob", new int[] {0, 2}),
            Map.entry("maxInstallsPerTick", new int[] {1, 100}),
            Map.entry("maxEncounterEvaluationsPerTick", new int[] {1, 64}),
            Map.entry("maxProbesPerTick", new int[] {8, 1024}),
            Map.entry("maxCompletionsPerTick", new int[] {1, 256}),
            Map.entry("maxSignalsPerTick", new int[] {1, 1024}),
            Map.entry("maxTickWorkMicros", new int[] {100, 20_000}),
            Map.entry("maxDecisionAgeMillis", new int[] {100, 5000}),
            Map.entry("lowConfidenceStreakForLocal", new int[] {1, 20}));

    @Test
    void standardValuesMatchTheSchemaTable() {
        var standard = DecisionSettings.standard();

        assertEquals(new DecisionSettings(600, 300, 8, 4, 600_000, 20, 1, 0.45, 0.15, 10, 2, 64, 16, 64, 2000, 1000, 3,
                48.0, false, false), standard);
    }

    @Test
    void everyIntegerLimitRejectsValuesJustOutsideItsRange() {
        INTEGER_RANGES.forEach((key, range) -> {
            for (var inside : range) {
                assertDoesNotThrow(() -> read(key + ": " + inside), key + " " + inside);
            }
            for (var outside : new int[] {range[0] - 1, range[1] + 1}) {
                var error = assertThrows(ConfigurationException.class, () -> read(key + ": " + outside), key);
                assertEquals("decision." + key, error.path());
            }
        });
    }

    @Test
    void decimalLimitsRejectValuesOutsideTheirRange() {
        for (var key : new String[] {"minimumConfidence", "tacticSwitchMargin"}) {
            assertDoesNotThrow(() -> read(key + ": 0.0"));
            assertDoesNotThrow(() -> read(key + ": 0.99"));
            assertEquals("decision." + key, assertThrows(ConfigurationException.class, () -> read(key + ": 1.0")).path());
            assertEquals("decision." + key, assertThrows(ConfigurationException.class, () -> read(key + ": -0.01")).path());
        }
        assertDoesNotThrow(() -> read("searchRadiusBlocks: 8"));
        assertDoesNotThrow(() -> read("searchRadiusBlocks: 128"));
        assertThrows(ConfigurationException.class, () -> read("searchRadiusBlocks: 7.9"));
        assertThrows(ConfigurationException.class, () -> read("searchRadiusBlocks: 128.1"));
    }

    @Test
    void perWorldLimitsNeverExceedTheGlobalOnes() throws Exception {
        assertEquals("decision.maxRequestsPerMinutePerWorld",
                assertThrows(ConfigurationException.class, () -> read("maxRequestsPerMinute: 100\n  maxRequestsPerMinutePerWorld: 101")).path());
        assertEquals("decision.maxInFlightRequestsPerWorld",
                assertThrows(ConfigurationException.class, () -> read("maxInFlightRequests: 2\n  maxInFlightRequestsPerWorld: 3")).path());
        var derived = read("maxRequestsPerMinute: 100\n  maxInFlightRequests: 2");
        assertEquals(100, derived.maxRequestsPerMinutePerWorld());
        assertEquals(2, derived.maxInFlightRequestsPerWorld());
    }

    @Test
    void decisionAgeDefaultFollowsTheJevTimeout() {
        assertEquals(1000, DecisionSettings.defaultMaxDecisionAgeMillis(800));
        assertEquals(2200, DecisionSettings.defaultMaxDecisionAgeMillis(2000));
        assertEquals(5000, DecisionSettings.defaultMaxDecisionAgeMillis(4900));
    }

    @Test
    void readsBooleansAndTheDebugScenarioSwitch() throws Exception {
        var configuration = new YamlConfiguration();
        configuration.loadFromString("decision:\n  logDecisions: true\ndebug:\n  recordScenarios: true\n");
        var settings = DecisionSettings.from(configuration, JevSettings.standard());

        assertTrue(settings.logDecisions());
        assertTrue(settings.recordScenarios());
        assertFalse(DecisionSettings.standard().recordScenarios());
    }

    private static DecisionSettings read(String decisionEntries) throws Exception {
        var configuration = new YamlConfiguration();
        configuration.loadFromString("decision:\n  " + decisionEntries + "\n");
        return DecisionSettings.from(configuration, JevSettings.standard());
    }

    @Test
    void theQuestionKeysOfM16HaveTheirDefaultsAndRanges() throws Exception {
        var standard = DecisionSettings.standard().questions();
        assertEquals(new QuestionSettings(8, QuestionDetail.FULL, false, true, 0.2, 0.7), standard);
        assertEquals(new FitGate.FitThresholds(0.2, 0.7), standard.fitThresholds());
        assertDoesNotThrow(() -> read("maxOptionsPerQuestion: 2"));
        assertDoesNotThrow(() -> read("maxOptionsPerQuestion: 32"));
        for (var outside : new int[] {1, 33}) {
            assertEquals("decision.maxOptionsPerQuestion", assertThrows(ConfigurationException.class,
                    () -> read("maxOptionsPerQuestion: " + outside)).path());
        }
        assertEquals(QuestionDetail.FULL, read("questionDetail: FULL").questions().questionDetail());
        assertEquals(QuestionDetail.STANDARD, read("questionDetail: standard").questions().questionDetail());
        var detail = assertThrows(ConfigurationException.class, () -> read("questionDetail: verbose"));
        assertEquals("decision.questionDetail", detail.path());
        assertTrue(detail.getMessage().contains("must be compact, standard or full"), detail.getMessage());
        assertEquals("decision.fitRescue", assertThrows(ConfigurationException.class,
                () -> read("fitVeto: 0.7\n  fitRescue: 0.7")).path());
        assertEquals("decision.fitVeto", assertThrows(ConfigurationException.class, () -> read("fitVeto: -0.1")).path());
        assertEquals("decision.fitChecks", assertThrows(ConfigurationException.class,
                () -> read("fitChecks: true\n  fitChecksAllowed: false")).path());
        var on = read("fitChecks: true\n  fitVeto: 0.1\n  fitRescue: 0.8").questions();
        assertTrue(on.fitChecks());
        assertEquals(new FitGate.FitThresholds(0.1, 0.8), on.fitThresholds());
    }

    @Test
    void offWasRemovedAndTheObsoleteCapDoesNotReplaceTheSessionCap() throws Exception {
        var error = assertThrows(ConfigurationException.class, () -> read("tickLayer:\n    mode: off"));
        assertTrue(error.getMessage().contains("removed; use casts"), error.getMessage());
        var warnings = new java.util.ArrayList<ConfigProblem>();
        var configuration = new YamlConfiguration();
        configuration.loadFromString("decision:\n  maxReadyChecksPerTick: 64\n");
        var settings = DecisionSettings.from(configuration, JevSettings.standard(), warnings::add);
        assertEquals(96, settings.maxSessionsPerTick());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().toString().contains("obsolete"));
    }
}
