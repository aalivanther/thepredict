package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternRulesTest {
    @Test
    void everyBetaRowActivatesOnSuccessesAndStaysQuietOnFailures() {
        assertEquals(1, PatternRules.VERSION);
        for (var pattern : EnemyPattern.values()) {
            if (!pattern.usesBeta()) {
                assertFalse(PatternRules.usesBeta(pattern));
                assertTrue(Double.isNaN(pattern.theta()));
                continue;
            }
            assertEquals(pattern.theta(), PatternRules.theta(pattern));
            assertEquals(pattern.group(), PatternRules.group(pattern));
            var occasion = PatternRules.occasion(pattern);
            assertTrue(PatternRules.matches(pattern, occasion));
            var other = occasion == EnemyAction.STRIKE ? EnemyAction.IDLE : EnemyAction.STRIKE;
            assertFalse(PatternRules.matches(pattern, other));
            assertEquals(PatternState.ACTIVE, feed(pattern, true).state());
            assertEquals(PatternState.INACTIVE, feed(pattern, false).state());
            var model = new akyrion.thepredict.engine.EnemyModel(new java.util.UUID(pattern.ordinal(), 1), 4, 2);
            for (var trial = 0; trial < 200; trial++) {
                model.learn(occasion, PatternSettings.DEFAULT);
            }
            assertTrue((model.activeMask() & pattern.bit()) != 0, pattern.name());
        }
    }

    @Test
    void aSpearDoesNotFeedTheRhythmPatterns() {
        assertFalse(PatternRules.rhythmSuccess(EnemyPattern.SPAMS_CLICKS, EnemyAction.STRIKE_EARLY, true));
        assertTrue(PatternRules.rhythmSuccess(EnemyPattern.SPAMS_CLICKS, EnemyAction.STRIKE_EARLY, false));
        assertFalse(PatternRules.rhythmSuccess(EnemyPattern.CRIT_JUMPER, EnemyAction.CRIT_STRIKE, true));
        assertFalse(PatternRules.kites(true, false));
        assertTrue(PatternRules.kites(true, true));
    }

    private static PatternTracker feed(EnemyPattern pattern, boolean success) {
        var tracker = new PatternTracker(pattern);
        for (var trial = 0; trial < 24; trial++) {
            tracker.observe(success, 1.0, 0.05, 8, 3, 0.05, 4.0);
        }
        return tracker;
    }
}
