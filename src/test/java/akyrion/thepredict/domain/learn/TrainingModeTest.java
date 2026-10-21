package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingModeTest {
    @Test
    void trainingRequiresTheMobFlagAndNeverAGlobalSwitch() throws Exception {
        assertFalse(TrainingEligibility.exploresWithPlayers(true, false, true));
        assertFalse(TrainingEligibility.exploresWithPlayers(false, true, true));
        assertTrue(TrainingEligibility.exploresWithPlayers(true, true, true));
        assertFalse(TrainingEligibility.exploresWithPlayers(true, true, false));
        assertFalse(TrainingEligibility.legal(false, true));
        assertTrue(TrainingEligibility.legal(true, true));
        var choice = ExplorePolicy.choose(3L, 4L, 1, 3, 0.10, 0, 10, 0.2, true, BehaviorPolicy.JEV_ARGMAX);
        assertEquals(BehaviorPolicy.JEV_ARGMAX, choice.behavior());
        assertFalse(choice.inStratum());
        ExploreArm armed = null;
        for (var seed = 1L; seed < 5000L; seed++) {
            var drawn = ExplorePolicy.choose(seed, 4L, 0, 2, 0.10, 0, 0, 0.2, false, BehaviorPolicy.JEV_ARGMAX);
            if (drawn.inStratum()) {
                armed = new ExploreArm(seed, 0, 2, 0.10, 0, 0, 0.2, false, BehaviorPolicy.JEV_ARGMAX, false);
                break;
            }
        }
        assertNotNull(armed);
        assertTrue(CaptureDecision.decide(armed, VoiArm.OFF, 4L).dropQuestions());
        assertFalse(CaptureDecision.decide(armed.accepted(), VoiArm.OFF, 4L).dropQuestions());
    }
}
