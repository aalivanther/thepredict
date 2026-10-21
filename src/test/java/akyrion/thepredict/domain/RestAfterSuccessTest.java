package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAfterSuccessTest {
    @Test
    void threeHitsOpenARestAndAMissClearsTheStreak() {
        var streak = 0;
        for (var hit = 0; hit < 3; hit++) {
            streak = RestStreak.next(streak, true);
        }
        assertTrue(RestStreak.opens(streak, PatternSettings.DEFAULT.restAfterSuccesses()));
        assertEquals(300, PatternSettings.DEFAULT.restTicks());
        var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
        for (var trial = 0; trial < 12; trial++) {
            tracker.observe(true, 1.0, 0.05, 8, 3, 0.05, 4.0);
        }
        tracker.rest();
        assertEquals(PatternState.RESTING, tracker.state());
        assertFalse(PatternSignals.activation(PatternCause.REST, tracker.state()));
        var filter = new PatternFactFilter();
        for (var tick = 0; tick < 40; tick++) {
            filter.update(EnemyPattern.SPAMS_CLICKS.bit(), tick, 40, 80, 0);
        }
        assertTrue((filter.shown() & EnemyPattern.SPAMS_CLICKS.bit()) != 0);
        filter.update(0, 40, 40, 80, EnemyPattern.SPAMS_CLICKS.bit());
        assertEquals(0, filter.shown() & EnemyPattern.SPAMS_CLICKS.bit());
        assertFalse(ExploitPolicy.allowsBias(PatternMode.OBSERVE, false, true));
        assertEquals(0, RestStreak.next(streak, false));
    }
}
