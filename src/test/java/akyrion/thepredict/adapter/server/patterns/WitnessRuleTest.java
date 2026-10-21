package akyrion.thepredict.adapter.server.patterns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitnessRuleTest {
    @Test
    void aWallAndInvisibilityHideVisualsButAHitAlwaysCounts() {
        assertFalse(WitnessRule.visual(false, false));
        assertFalse(WitnessRule.visual(true, true));
        assertTrue(WitnessRule.visual(true, false));
        assertTrue(WitnessRule.audible(10.0 * 10.0, 16.0 * 16.0));
        assertFalse(WitnessRule.audible(20.0 * 20.0, 16.0 * 16.0));
        assertTrue(WitnessRule.receivedHit());
    }
}
