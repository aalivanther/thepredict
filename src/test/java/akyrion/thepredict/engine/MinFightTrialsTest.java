package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PatternTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinFightTrialsTest {
    @Test
    void threeOwnTrialsStayInactiveAndTheFourthMayEnter() {
        var pattern = EnemyPattern.values()[0];
        var tracker = new PatternTracker(pattern);
        tracker.evidence().seed(6, 0);
        for (var index = 0; index < 3; index++) {
            tracker.observe(true, 1, 0.05, 8, 1, 0.05, 4, 4);
        }
        assertNotEquals(PatternState.ACTIVE, tracker.state());
        assertTrue(tracker.evidence().trials() < 4);
        tracker.observe(true, 1, 0.05, 8, 1, 0.05, 4, 4);
        assertTrue(tracker.evidence().trials() >= 4);
    }
}
