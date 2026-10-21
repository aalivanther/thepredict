package akyrion.thepredict;

import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The candidate a pattern-mode verdict names (CAR-02): decision limits change it; the mode and budgets do not. */
class PatternFingerprintTest {
    @Test
    void aDecisionLimitChangesTheFingerprintAndTheModeOrABudgetDoesNot() {
        var base = PatternSettings.DEFAULT;
        var sha = PatternFingerprint.of(base);
        assertEquals(64, sha.length());
        assertEquals(sha, PatternFingerprint.of(copy(base, PatternMode.FULL, base.maxReadsPerTick(),
                base.maxMicrosPerTick(), base.minTrials())));
        assertEquals(sha, PatternFingerprint.of(copy(base, base.mode(), 32, 120, base.minTrials())));
        assertNotEquals(sha, PatternFingerprint.of(copy(base, base.mode(), base.maxReadsPerTick(),
                base.maxMicrosPerTick(), base.minTrials() + 1)));
    }

    @Test
    void theCanonicalTextNamesTheRuleVersionsAndKeepsKeysInOrder() {
        var text = PatternFingerprint.canonical(PatternSettings.DEFAULT);
        assertTrue(text.startsWith("{\"airSwings\":\"OBSERVE\","), text);
        assertTrue(text.contains("\"counters\":1") && text.contains("\"rules\":1"), text);
        assertTrue(!text.contains("mode") && !text.contains("maxReadsPerTick") && !text.contains("maxMicrosPerTick"),
                text);
    }

    private static PatternSettings copy(PatternSettings d, PatternMode mode, int reads, int micros, int minTrials) {
        return new PatternSettings(mode, d.hearingBlocks(), d.sampleTicks(), d.maxWatchedPerEncounter(), reads,
                d.strafeBlocks(), d.airSwings(), d.mergeTicks(), d.idleTicks(), d.maxTokensPerSecond(),
                d.order2Contexts(), d.forgetting(), d.evidenceForgetting(), d.confidenceQuantile(), minTrials,
                d.persistUpdates(), d.exitMargin(), d.changepointH(), d.forecastMinAccuracy(), d.histogramBinTicks(),
                micros, d.maxExploitShare(), d.maxCounterDelayTicks(), d.exploitWindowTicks(),
                d.restAfterSuccesses(), d.restTicks(), d.maxExploitedPatterns(), d.factOnTicks(), d.factOffTicks(),
                d.maxFactsPerParticipant(), d.maxFactsPerRequest());
    }
}
