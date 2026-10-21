package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.NativeLevelMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The next spawn moves by one only when every gate is open (M22.12). */
class NativeLevelAdjusterTest {
    @Test
    void aClosedGateLeavesTheLevelAndAnOpenGateStepsInsideTheBand() {
        assertEquals(10, NativeLevelAdjuster.nextLevel(10, 1, false, NativeLevelMode.SPAWN, true));
        assertEquals(10, NativeLevelAdjuster.nextLevel(10, 1, true, NativeLevelMode.OFF, true));
        assertEquals(10, NativeLevelAdjuster.nextLevel(10, 1, true, NativeLevelMode.SPAWN, false));
        assertEquals(10, NativeLevelAdjuster.nextLevel(10, 0, true, NativeLevelMode.SPAWN, true));
        assertEquals(11, NativeLevelAdjuster.nextLevel(10, 4, true, NativeLevelMode.SPAWN, true));
        assertEquals(9, NativeLevelAdjuster.nextLevel(10, -3, true, NativeLevelMode.SPAWN, true));
        assertEquals(13, NativeLevelAdjuster.nextLevel(12, 1, true, NativeLevelMode.SPAWN, true));
        assertEquals(11, NativeLevelAdjuster.nextLevel(12, -1, true, NativeLevelMode.SPAWN, true));
    }
}
