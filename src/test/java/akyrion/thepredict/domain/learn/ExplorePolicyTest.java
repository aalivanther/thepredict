package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorePolicyTest {
    @Test
    void mixtureSumsToOneAndAFrequencyDrawMatchesIt() {
        var pi = ExplorePolicy.propensities(0, 4, 0.10);
        var sum = 0.0;
        for (var value : pi) {
            sum += value;
        }
        assertEquals(1.0, sum, 1.0e-12);
        assertEquals(0.925, pi[0], 1.0e-12);
        var counts = new int[4];
        var draws = 100_000;
        for (var index = 0; index < draws; index++) {
            var word = akyrion.thepredict.domain.StableRandom.draw(99L, index, 6);
            counts[ExplorePolicy.sample(word, pi)]++;
        }
        for (var index = 0; index < 4; index++) {
            assertEquals(pi[index], counts[index] / (double) draws, 0.005);
        }
        var blocked = ExplorePolicy.choose(1L, 2L, 0, 4, 0.10, 0, 0, 0.2, true, BehaviorPolicy.LOCAL_ARGMAX);
        assertFalse(blocked.inStratum());
        assertEquals(BehaviorPolicy.LOCAL_ARGMAX, blocked.behavior());
        assertEquals(1.0, blocked.propensity());
        assertTrue(BehaviorPolicy.JEV_ARGMAX.deterministic());
        assertFalse(BehaviorPolicy.EXPLORE_UNIFORM.deterministic());
    }
}
