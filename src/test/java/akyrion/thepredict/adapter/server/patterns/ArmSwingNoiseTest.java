package akyrion.thepredict.adapter.server.patterns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmSwingNoiseTest {
    @Test
    void observeNeverTokensAndACleanSwordSwingDoes() {
        assertFalse(AirSwingGate.tokens(false, true, true, false, false, false));
        assertFalse(AirSwingGate.tokens(true, true, true, true, false, false));
        assertFalse(AirSwingGate.tokens(true, true, false, false, false, false));
        assertFalse(AirSwingGate.tokens(true, true, true, false, true, false));
        assertFalse(AirSwingGate.tokens(true, true, true, false, false, true));
        assertTrue(AirSwingGate.tokens(true, true, true, false, false, false));
    }
}
