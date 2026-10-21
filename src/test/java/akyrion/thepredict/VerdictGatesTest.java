package akyrion.thepredict;

import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemoryPolicy;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.engine.MemoryModeLoader;
import akyrion.thepredict.engine.PatternModeGate;
import akyrion.thepredict.engine.StorageReply;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAR-02 and CAR-07: the pattern and memory modes open with the verdicts of their own fingerprint and stages. */
class VerdictGatesTest {
    @Test
    void biasNeedsAPassingSimulatorVerdictForThePatternFingerprint() {
        var settings = patterns(PatternMode.BIAS);
        var sha = PatternFingerprint.of(settings);
        var gate = new PatternModeGate();
        var verdicts = new VerdictGates();
        verdicts.load(List.of(row("0".repeat(64), "G1", "PASS", 5)));
        assertEquals(Optional.empty(), verdicts.offerPatterns(gate, settings));
        assertEquals(PatternMode.OBSERVE, gate.effective());
        assertEquals(PatternModeGate.UNVERIFIED, gate.cause());
        verdicts.load(List.of(row(sha, "G1", "PASS_SIM_ONLY", 6)));
        assertEquals(Optional.empty(), verdicts.offerPatterns(gate, settings));
        assertEquals(PatternMode.BIAS, gate.effective());
        assertEquals("", gate.cause());
        verdicts.load(List.of(row(sha, "G1", "FAIL", 7), row(sha, "G1", "PASS", 3)));
        verdicts.offerPatterns(gate, settings);
        assertEquals(PatternMode.OBSERVE, gate.effective(), "the later verdict wins");
    }

    @Test
    void fullNeedsTheBenchStageAsWellOrFallsBackToBiasWithAWarning() {
        var settings = patterns(PatternMode.FULL);
        var sha = PatternFingerprint.of(settings);
        var gate = new PatternModeGate();
        var verdicts = new VerdictGates();
        verdicts.load(List.of(row(sha, "G1", "PASS", 1)));
        var warning = verdicts.offerPatterns(gate, settings);
        assertEquals(PatternMode.BIAS, gate.effective());
        assertTrue(warning.orElseThrow().startsWith("patterns.mode full has no passing G2 verdict for fingerprint "
                + sha), warning.toString());
        verdicts.load(List.of(row(sha, "G2", "PASS", 2)));
        assertEquals(Optional.empty(), verdicts.offerPatterns(gate, settings));
        assertEquals(PatternMode.FULL, gate.effective());
    }

    @Test
    void memoryFullWithOnlyTheSimulatorStageRunsSeedAndWithBothRunsFull() {
        var settings = memory(MemoryMode.FULL, false);
        var sha = MemoryPolicy.fingerprint(settings);
        var loader = new MemoryModeLoader();
        var verdicts = new VerdictGates();
        verdicts.load(List.of());
        verdicts.offerMemory(loader, settings);
        loader.tick();
        assertEquals(MemoryMode.RECORD, loader.active());
        assertTrue(loader.unverified());
        verdicts.load(List.of(row(sha, "G1", "PASS", 1)));
        assertTrue(verdicts.offerMemory(loader, settings).isPresent());
        loader.tick();
        assertEquals(MemoryMode.SEED, loader.active());
        verdicts.load(List.of(row(sha, "G2", "PASS", 2)));
        assertEquals(Optional.empty(), verdicts.offerMemory(loader, settings));
        loader.tick();
        assertEquals(MemoryMode.FULL, loader.active());
        assertFalse(loader.unverified());
    }

    @Test
    void allowUnverifiedStillAppliesTheRequestedMemoryMode() {
        var loader = new MemoryModeLoader();
        new VerdictGates().offerMemory(loader, memory(MemoryMode.FULL, true));
        loader.tick();
        assertEquals(MemoryMode.FULL, loader.active());
    }

    @Test
    void theListIsPendingBeforeTheLoadAndCappedAfterIt() {
        var verdicts = new VerdictGates();
        assertEquals(List.of("pending: the verdicts have not loaded yet"), verdicts.lines(4));
        var rows = new ArrayList<StorageReply.GateVerdictRow>();
        for (var index = 0; index < 6; index++) {
            rows.add(row(Integer.toString(index).repeat(64), "G1", "PASS", index));
        }
        verdicts.load(rows);
        assertEquals(List.of("000000000000 G1 PASS", "111111111111 G1 PASS", "222222222222 G1 PASS",
                "3 more verdicts are not shown"), verdicts.lines(4));
        assertEquals(6, verdicts.lines(10).size());
    }

    private static StorageReply.GateVerdictRow row(String sha, String gate, String verdict, long epoch) {
        return new StorageReply.GateVerdictRow(sha, gate, verdict, "run", epoch);
    }

    private static PatternSettings patterns(PatternMode mode) {
        var d = PatternSettings.DEFAULT;
        return new PatternSettings(mode, d.hearingBlocks(), d.sampleTicks(), d.maxWatchedPerEncounter(),
                d.maxReadsPerTick(), d.strafeBlocks(), d.airSwings(), d.mergeTicks(), d.idleTicks(),
                d.maxTokensPerSecond(), d.order2Contexts(), d.forgetting(), d.evidenceForgetting(),
                d.confidenceQuantile(), d.minTrials(), d.persistUpdates(), d.exitMargin(), d.changepointH(),
                d.forecastMinAccuracy(), d.histogramBinTicks(), d.maxMicrosPerTick(), d.maxExploitShare(),
                d.maxCounterDelayTicks(), d.exploitWindowTicks(), d.restAfterSuccesses(), d.restTicks(),
                d.maxExploitedPatterns(), d.factOnTicks(), d.factOffTicks(), d.maxFactsPerParticipant(),
                d.maxFactsPerRequest());
    }

    private static MemorySettings memory(MemoryMode mode, boolean allowUnverified) {
        var d = MemorySettings.DEFAULT;
        return new MemorySettings(mode, d.fightDecay(), d.halfLifeDays(), d.crossFightH(), d.styleBreakFactor(),
                d.seedMaxTrials(), d.minFightTrials(), d.rungPriorFights(), d.maxRungOffset(),
                d.minDamageShareRatio(), d.maxProfilesPerPlayer(), d.maxFactsPerRequest(), d.factMaxAgeDays(),
                d.cacheSubjects(), d.maxHintsPerTick(), d.loadTimeoutSeconds(), d.refreshSeconds(),
                d.maxPendingDigestsPerSubject(), d.maxPendingSubjects(), d.maxSubjectsPerBatch(), d.retentionDays(),
                d.maxRows(), allowUnverified, d.backend());
    }
}
