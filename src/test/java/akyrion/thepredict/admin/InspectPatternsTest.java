package akyrion.thepredict.admin;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectPatternsTest {
    @Test
    void theBlockNamesStateConfidenceTrialsAndQuota() {
        var text = PatternInspect.format("strike strafe_left", "none", 420, EnemyPattern.SPAMS_CLICKS,
                PatternState.ACTIVE, 80, 12, 3, false, 2, 5, "spams_clicks");
        assertTrue(text.contains("tokens strike strafe_left"));
        assertTrue(text.contains("spams_clicks active"));
        assertTrue(text.contains("confidence 80"));
        assertTrue(text.contains("trials 12"));
        assertTrue(text.contains("quota 2/5"));
        assertTrue(text.contains("facts spams_clicks"));
    }
}
