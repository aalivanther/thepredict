package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightEventKind;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The clock engages only on damage dealt to the current target (M22.8, errata E22-5). */
class FightClockTest {
    @Test
    void dealtEngagesAndAStrangerDoesNot() {
        var clock = new FightClock();
        var target = UUID.fromString("00000000-0000-0000-0000-000000000022");
        var other = UUID.fromString("00000000-0000-0000-0000-000000000023");
        assertEquals(Optional.empty(), clock.dealt(10L, other, target, 0.1, 0.9));
        assertFalse(clock.open());
        assertEquals(Optional.of(FightEventKind.ENGAGED), clock.dealt(11L, target, target, 0.2, 0.8));
        assertEquals(11L, clock.startTick());
        assertEquals(Optional.empty(), clock.dealt(12L, target, target, 0.1, 0.7));
        assertEquals(0.3, clock.dealt(), 1e-9);
        assertEquals(0.7, clock.healthRemaining(), 1e-9);
        assertEquals(Optional.empty(), clock.quiet(210L, 200));
        assertEquals(Optional.of(FightEventKind.DISENGAGED), clock.quiet(212L, 200));
        assertFalse(clock.open());
    }
}
