package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.EnemyBand;
import akyrion.thepredict.domain.distill.FightPhase;
import akyrion.thepredict.domain.distill.RegimeKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegimeTelegraphTest {
    @Test
    void anOpenTelegraphBlocksADueSwitch() {
        var selector = new RegimeSelector(40, 6);
        selector.offer(RegimeKey.of(EnemyBand.MANY, FightPhase.MID, java.util.List.of()), 0L);
        selector.telegraph(true);
        assertFalse(selector.onCapture(40L));
        assertEquals(RegimeKey.ALL, selector.current());
        assertFalse(selector.capped(40L));
    }
}
