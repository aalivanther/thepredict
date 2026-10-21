package akyrion.thepredict.engine.learn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InFightWeightsTest {
    @Test
    void theFactorStaysInsideTheBandAndDecays() {
        var weights = new InFightWeights(2);
        assertEquals(1000, weights.factorMillis(0));
        for (var step = 0; step < 40; step++) {
            weights.observe(0, 2.0, 0.0);
        }
        assertTrue(weights.factorMillis(0) <= 1100);
        assertTrue(weights.factorMillis(0) >= 900);
        var high = weights.factorMillis(0);
        weights.observe(0, 0.0, 0.0);
        assertTrue(weights.factorMillis(0) <= high);
    }

    @Test
    void twoTacticsKeepTheirOwnFactorWhateverTheirPlaceInTheList() {
        var learning = new SessionLearning();
        for (var step = 0; step < 5; step++) {
            learning.observeWeight(true, true, "press", 1.0, 0.0);
            learning.observeWeight(true, true, "guard", -1.0, 0.0);
        }
        var forward = learning.localWeights(true, List.of("press", "guard", "unseen"));
        var backward = learning.localWeights(true, List.of("unseen", "guard", "press"));
        assertTrue(forward[0] > 1.0);
        assertTrue(forward[1] < 1.0);
        assertEquals(1.0, forward[2], 0.0);
        assertArrayEquals(new double[] {forward[2], forward[1], forward[0]}, backward, 0.0);
        assertArrayEquals(new double[] {1.0, 1.0}, learning.localWeights(false, List.of("press", "guard")), 0.0);
    }

    @Test
    void aLaterSlotGrowsTheTableWithoutForgettingTheEarlierOnes() {
        var learning = new SessionLearning();
        for (var step = 0; step < 3; step++) {
            learning.observeWeight(true, true, 0, 1.0, 0.0);
        }
        var earned = learning.localWeight(true);
        assertTrue(earned > 1.0);
        learning.observeWeight(true, true, 12, -1.0, 0.0);
        assertEquals(earned, learning.localWeight(true), 0.0);
        assertTrue(learning.localWeights(true, 13)[12] < 1.0);
    }

    @Test
    void tacticsPastTheCapStayNeutral() {
        var weights = new InFightWeights(0);
        for (var tactic = 0; tactic < InFightWeights.MAX_TACTICS + 6; tactic++) {
            weights.observe("t" + tactic, 1.0, 0.0);
        }
        assertTrue(weights.factorMillis("t0") > 1000);
        assertEquals(1000, weights.factorMillis("t" + InFightWeights.MAX_TACTICS));
        assertEquals(InFightWeights.MAX_TACTICS, weights.size());
        weights.observe(InFightWeights.MAX_TACTICS + 3, 1.0, 0.0);
        assertEquals(InFightWeights.MAX_TACTICS, weights.size());
    }
}
