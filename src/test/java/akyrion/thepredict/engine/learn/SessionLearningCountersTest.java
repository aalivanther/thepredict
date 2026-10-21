package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.HeldIntent;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ExplorePolicy;
import akyrion.thepredict.domain.learn.VoiDecision;
import akyrion.thepredict.domain.learn.VoiPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The counters that the arms read: skips in a row (M28.8) and the exploration share (M28.5). */
class SessionLearningCountersTest {
    @Test
    void twoSkipsInARowAndTheThirdCaptureAsks() {
        var learning = new SessionLearning();
        var maxSkips = 2;
        var skips = 0;
        for (var capture = 0; capture < 6; capture++) {
            var decision = VoiPolicy.evaluate(true, "on", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02,
                    learning.consecutiveSkips(), maxSkips, false);
            if (decision.skip()) {
                skips++;
                learning.hold(new HeldIntent(new TacticId("press"), BehaviorPolicy.VOI_SKIP, 1, capture, capture + 40));
            }
            learning.noteCaptured(1);
            if (capture % 3 == 2) {
                assertEquals(VoiDecision.ASK, decision, "capture " + capture);
                assertEquals(0, learning.consecutiveSkips());
            }
        }
        assertEquals(4, skips);
    }

    @Test
    void aHeldIntentThatIsNotASkipDoesNotCount() {
        var learning = new SessionLearning();
        learning.hold(new HeldIntent(new TacticId("press"), BehaviorPolicy.DISTILLED, 1, 0, 40));
        learning.noteCaptured(1);
        assertEquals(0, learning.consecutiveSkips());
    }

    @Test
    void ordinaryAdoptionsBringTheExplorationShareBackUnderTheCap() {
        var learning = new SessionLearning();
        learning.noteAdoption(true);
        learning.noteWindow("v2:MELEE:HIGH:HIGH:MOB:NA:SOLO:NONE", 0.0);
        assertEquals(1, learning.exploredCount());
        assertEquals(1, learning.adoptionCount());
        assertTrue(over(learning));
        for (var window = 0; window < 4; window++) {
            learning.noteWindow("v2:MELEE:HIGH:HIGH:MOB:NA:SOLO:NONE", 0.0);
        }
        assertTrue(over(learning), "1 of 5 is the 0.2 cap");
        learning.noteWindow("v2:MELEE:HIGH:HIGH:MOB:NA:SOLO:NONE", 0.0);
        assertFalse(over(learning), "1 of 6 is under the cap");
    }

    private static boolean over(SessionLearning learning) {
        for (var seed = 1L; seed < 2_000L; seed++) {
            var choice = ExplorePolicy.choose(seed, 4L, 0, 3, 0.5, learning.exploredCount(), learning.adoptionCount(),
                    0.2, false, BehaviorPolicy.JEV_ARGMAX);
            if (choice.inStratum()) {
                return false;
            }
        }
        return true;
    }
}
