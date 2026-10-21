package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickLayerLoadTest {
    @Test
    void threeTicksAtTheCapRaiseAStepAndFortyQuietTicksLowerIt() {
        var degrade = new TickDegrade();
        degrade.note(true, false);
        degrade.note(true, false);
        assertEquals(0, degrade.step());
        degrade.note(true, false);
        assertEquals(1, degrade.step());
        for (var tick = 0; tick < 39; tick++) {
            degrade.note(false, true);
        }
        assertEquals(1, degrade.step());
        degrade.note(false, true);
        assertEquals(0, degrade.step());
        assertTrue(TickDegrade.RISE_TICKS == 3 && TickDegrade.FALL_TICKS == 40);
    }
}
