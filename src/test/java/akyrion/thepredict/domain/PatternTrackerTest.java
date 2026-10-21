package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A seeded pattern activates after the configured own trials of the fight, no more (A15, memory.minFightTrials). */
class PatternTrackerTest {
    private static final double QUANTILE = 0.10;
    private static final int MIN_TRIALS = 4;

    @Test
    void aSeededPatternActivatesAfterTheConfiguredOwnTrials() {
        for (var own = 1; own <= 4; own++) {
            var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
            tracker.evidence().seed(6.0, 0.0);
            var activatedAt = -1;
            for (var trial = 1; trial <= 6 && activatedAt < 0; trial++) {
                var cause = tracker.observe(true, 1.0, QUANTILE, MIN_TRIALS, 1, 0.1, 4.0, own);
                if (cause.equals(Optional.of(PatternCause.ENTER))) {
                    activatedAt = trial;
                }
            }
            assertEquals(own, activatedAt, "minFightTrials " + own);
            assertEquals(PatternState.ACTIVE, tracker.state());
        }
    }

    @Test
    void aSeededPatternWithoutAConfiguredValueWaitsForTheDefaultOwnTrials() {
        var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
        tracker.evidence().seed(6.0, 0.0);
        var activatedAt = -1;
        for (var trial = 1; trial <= 8 && activatedAt < 0; trial++) {
            if (tracker.observe(true, 1.0, QUANTILE, MIN_TRIALS, 1, 0.1, 4.0, 0).isPresent()) {
                activatedAt = trial;
            }
        }
        assertEquals(MemorySettings.DEFAULT.minFightTrials(), activatedAt);
    }

    @Test
    void withoutASeedTheDetectorTrialsDecide() {
        var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
        var activatedAt = -1;
        for (var trial = 1; trial <= 20 && activatedAt < 0; trial++) {
            if (tracker.observe(true, 1.0, QUANTILE, MIN_TRIALS, 1, 0.1, 4.0).isPresent()) {
                activatedAt = trial;
            }
        }
        assertTrue(activatedAt >= MIN_TRIALS, "activated at " + activatedAt);
        assertEquals(0, tracker.evidence().seededTrials());
    }
}
