package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.EnemyBand;
import akyrion.thepredict.domain.distill.FightPhase;
import akyrion.thepredict.domain.distill.RegimeKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dwell is counted from the event. A telegraph blocks the switch. The cap is six. */
class RegimeSelectorTest {
    @Test
    void aSwitchWaitsFortyTicksAndDoesNotMoveDuringATelegraph() {
        var selector = new RegimeSelector(40, 6);
        var next = RegimeKey.of(EnemyBand.FEW, FightPhase.MID, java.util.List.of());
        selector.offer(next, 10L);
        assertFalse(selector.onCapture(49L));
        assertEquals(RegimeKey.ALL, selector.current());
        selector.telegraph(true);
        assertFalse(selector.onCapture(50L));
        selector.telegraph(false);
        assertTrue(selector.onCapture(50L));
        assertEquals(next, selector.current());
        assertEquals(1, selector.switches());
    }

    @Test
    void theSixthSwitchIsTheLast() {
        var selector = new RegimeSelector(40, 6);
        for (var index = 0; index < 6; index++) {
            var key = RegimeKey.of(index % 2 == 0 ? EnemyBand.ONE : EnemyBand.MANY, FightPhase.LATE,
                    java.util.List.of());
            selector.offer(key, index * 100L);
            assertTrue(selector.onCapture(index * 100L + 40L));
        }
        selector.offer(RegimeKey.of(EnemyBand.FEW, FightPhase.OPENING, java.util.List.of()), 1_000L);
        assertFalse(selector.onCapture(1_040L));
        assertTrue(selector.capped(1_040L));
        assertEquals(6, selector.switches());
    }
}
