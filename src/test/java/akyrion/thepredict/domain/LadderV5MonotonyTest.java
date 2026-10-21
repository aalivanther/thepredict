package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LadderV5MonotonyTest {
    @Test
    void exploitShareDoesNotFallAsTheRungRisesAndRhythmDoesNotLoosen() {
        for (var rung = 1; rung <= 8; rung++) {
            var lower = PressureLadder.levers(rung - 1, 0, 0, true);
            var higher = PressureLadder.levers(rung, 0, 0, true);
            assertTrue(higher.exploitShare() + 1e-9 >= lower.exploitShare());
            assertTrue(higher.reactionDelayTicks() <= lower.reactionDelayTicks());
            assertTrue(higher.damagingCastSlackTicks() <= lower.damagingCastSlackTicks());
        }
        assertTrue(PressureLadder.bundledMatches());
    }
}
