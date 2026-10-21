package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.ExploitPolicy;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.VarietyPicker;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pattern step stores on the session the cap of patterns it may exploit at once (DAL-04), so the exploit query of
 * the local pick ({@code ExploitPolicy.allowsNow}) reads the cap of the profile and not the delivered default.
 */
class PatternBiasTest {
    private static final int CONFIGURED = 1;

    @Test
    void aLiveStepStoresTheConfiguredVarietyOnTheSession() {
        var session = session();
        new PatternBias().apply(session, null, withExploited(CONFIGURED), PatternMode.BIAS, false, null, 5L);
        assertNotEquals(PatternSettings.DEFAULT.maxExploitedPatterns(), CONFIGURED, "the test needs a non-default cap");
        assertEquals(ExploitPolicy.variety(CONFIGURED, session.entropyActive(), session.patternPass()),
                session.patternVariety());
    }

    @Test
    void anObservingStepStoresTheConfiguredCapWithoutTheEntropyLift() {
        var session = session();
        new PatternBias().apply(session, null, withExploited(CONFIGURED), PatternMode.OBSERVE, false, null, 5L);
        assertEquals(CONFIGURED, session.patternVariety());
    }

    /**
     * E-11: the variety pick runs again only when the active mask, a confidence, the held bits or the cap of the
     * session changed; in between, the session keeps the bits the pick returned, which are the bits it would return.
     */
    @Test
    void theVarietyIsPickedAgainOnlyWhenItsInputsChange() {
        var bias = new PatternBias();
        var enemy = new UUID(4, 4);
        var model = new EnemyModel(enemy, 4, 2);
        for (var strike = 0; strike < 16; strike++) {
            model.learn(EnemyAction.STRIKE_EARLY, PatternSettings.DEFAULT);
        }
        assertTrue((model.activeMask() & EnemyPattern.SPAMS_CLICKS.bit()) != 0, "the strikes activated the habit");
        var session = session();
        for (var tick = 5L; tick < 12L; tick++) {
            bias.apply(session, model, PatternSettings.DEFAULT, PatternMode.BIAS, false, enemy, tick);
        }
        assertEquals(2, bias.selections(), "the first step picks, the second picks with its own result as the held"
                + " bits, and from then on the same inputs keep the same pick");
        assertEquals(fresh(model, session), session.patternHeld());
        model.learn(EnemyAction.STRIKE_EARLY, PatternSettings.DEFAULT);
        bias.apply(session, model, PatternSettings.DEFAULT, PatternMode.BIAS, false, enemy, 12L);
        bias.apply(session, model, PatternSettings.DEFAULT, PatternMode.BIAS, false, enemy, 13L);
        assertEquals(3, bias.selections(), "a new confidence picks once more");
        assertEquals(fresh(model, session), session.patternHeld());
        var other = new EngineFixtures.Sessions().registry.open(EngineFixtures.id(2),
                CoordinatorFixture.profile(20, 20, 1));
        bias.apply(other, model, PatternSettings.DEFAULT, PatternMode.BIAS, false, enemy, 13L);
        assertEquals(4, bias.selections(), "each session keeps its own pick");
        assertEquals(session.patternHeld(), other.patternHeld());
    }

    private static int fresh(EnemyModel model, MobSession session) {
        var confidence = new double[EnemyPattern.values().length];
        for (var pattern : EnemyPattern.values()) {
            var tracker = model.tracker(pattern);
            confidence[pattern.ordinal()] = tracker == null ? 0.0 : tracker.evidence().mean();
        }
        return VarietyPicker.select(model.activeMask(), confidence, session.patternHeld(), session.patternVariety());
    }

    private static MobSession session() {
        var sessions = new EngineFixtures.Sessions();
        return sessions.registry.open(EngineFixtures.id(1), CoordinatorFixture.profile(20, 20, 1));
    }

    private static PatternSettings withExploited(int maxExploitedPatterns) {
        var d = PatternSettings.DEFAULT;
        return new PatternSettings(d.mode(), d.hearingBlocks(), d.sampleTicks(), d.maxWatchedPerEncounter(),
                d.maxReadsPerTick(), d.strafeBlocks(), d.airSwings(), d.mergeTicks(), d.idleTicks(),
                d.maxTokensPerSecond(), d.order2Contexts(), d.forgetting(), d.evidenceForgetting(),
                d.confidenceQuantile(), d.minTrials(), d.persistUpdates(), d.exitMargin(), d.changepointH(),
                d.forecastMinAccuracy(), d.histogramBinTicks(), d.maxMicrosPerTick(), d.maxExploitShare(),
                d.maxCounterDelayTicks(), d.exploitWindowTicks(), d.restAfterSuccesses(), d.restTicks(),
                maxExploitedPatterns, d.factOnTicks(), d.factOffTicks(), d.maxFactsPerParticipant(),
                d.maxFactsPerRequest());
    }
}
