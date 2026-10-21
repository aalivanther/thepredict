package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedCadenceTest {
    @Test
    void aZombieHittingEveryTwentyTicksActivatesWithinTenHits() {
        var gaps = new int[9];
        for (var index = 0; index < gaps.length; index++) {
            gaps[index] = 20;
        }
        assertTrue(FixedCadence.active(gaps));
        assertFalse(FixedCadence.active(new int[] {20, 20, 20, 20, 20, 20, 20}));
        assertFalse(FixedCadence.active(new int[] {10, 12, 18, 30, 8, 40, 15, 22, 50}));
    }
}
