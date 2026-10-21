package akyrion.thepredict.adapter.server.patterns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearingRangeTest {
    @Test
    void sixteenMembersHearATotemAtFifteenPointNineAndNotAtSixteenPointOne() {
        var hearing = 16.0 * 16.0;
        var distances = new double[16];
        for (var index = 0; index < distances.length; index++) {
            distances[index] = 40.0 * 40.0;
        }
        distances[7] = 15.9 * 15.9;
        assertTrue(WitnessRule.audible(nearest(distances), hearing));
        distances[7] = 16.1 * 16.1;
        assertFalse(WitnessRule.audible(nearest(distances), hearing));
    }

    private static double nearest(double[] squared) {
        var best = squared[0];
        for (var value : squared) {
            if (value < best) {
                best = value;
            }
        }
        return best;
    }
}
