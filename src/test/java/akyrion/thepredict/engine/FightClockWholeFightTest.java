package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightEventKind;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One clock for the whole fight: a new target does not restart it (M22.13). */
class FightClockWholeFightTest {
    @Test
    void aTargetChangeKeepsTheOriginalStart() {
        var clock = new FightClock();
        var first = UUID.fromString("00000000-0000-0000-0000-000000000031");
        var second = UUID.fromString("00000000-0000-0000-0000-000000000032");
        assertEquals(Optional.of(FightEventKind.ENGAGED), clock.dealt(5L, first, first, 0.1, 0.9));
        assertEquals(Optional.of(FightEventKind.TARGET_CHANGED), clock.dealt(40L, second, second, 0.2, 0.8));
        assertEquals(5L, clock.startTick());
        assertEquals(Optional.of(second), clock.target());
        assertEquals(35L, clock.elapsed(40L));
        assertEquals(Optional.of(FightEventKind.TARGET_DIED), clock.targetDied(50L));
        assertTrue(clock.targetDied());
        assertEquals(Optional.of(FightEventKind.ENGAGED), new FightClock().dealt(1L, first, first, 0.05, 0.95));
    }
}
