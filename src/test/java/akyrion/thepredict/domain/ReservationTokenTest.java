package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationTokenTest {
    @Test
    void aMeleeHoldYieldsAndNeverReleases() {
        assertEquals(25, ReservationHold.meleeLimit(30));
        assertFalse(ReservationHold.mayHold(SkillRole.ESCAPE));
        assertFalse(ReservationHold.mayHold(SkillRole.SUSTAIN));
        assertTrue(ReservationHold.mayHold(SkillRole.INTERRUPT));
        assertTrue(ReservationHold.giveBack(10, 30).isEmpty());
        var end = ReservationHold.giveBack(25, 30).orElseThrow();
        assertEquals(TokenEnd.YIELDED, end);
        assertTrue(end != TokenEnd.RELEASED);
        assertEquals(TokenEnd.YIELDED, TokenEnd.values()[TokenEnd.values().length - 1]);
        assertTrue(TokenEnd.IDLE_TIMEOUT != TokenEnd.YIELDED);
    }
}
