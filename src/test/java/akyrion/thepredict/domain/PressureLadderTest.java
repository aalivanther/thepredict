package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ladder v1, nine rungs, players and entities (M22.7). */
class PressureLadderTest {
    @Test
    void nineRungsTimesPlayerAndEntityMatchTheTableAndTheBenchIdentity() {
        assertTrue(PressureLadder.bundledMatches());
        int[] delay = {20, 18, 16, 14, 12, 10, 8, 6, 0};
        int[] slack = {20, 16, 12, 8, 6, 4, 2, 0, 0};
        for (var rung = 0; rung <= 8; rung++) {
            for (var player : new boolean[] {true, false}) {
                for (var floor : new int[] {0, 5}) {
                    var levers = PressureLadder.levers(rung, 0, floor, player);
                    var expectedDelay = rung == 8 ? (player ? floor : 0) : delay[rung];
                    if (player) {
                        expectedDelay = Math.max(expectedDelay, floor);
                    }
                    assertEquals(expectedDelay, levers.reactionDelayTicks(), "rung " + rung);
                    assertEquals(slack[rung], levers.damagingCastSlackTicks());
                    assertEquals(rung >= 5, levers.damagePreference());
                    assertEquals(rung <= 3 ? 150 : 0, levers.localDamagePenaltyMillis());
                    assertTrue(ReactionMask.admits(levers.reactionMask(), Reaction.EVADE_CRITICAL));
                    assertTrue(ReactionMask.admits(levers.reactionMask(), Reaction.DODGE_SHOT));
                    assertTrue(ReactionMask.admits(levers.reactionMask(), Reaction.SPACE_OUT));
                    assertTrue(ReactionMask.admits(levers.reactionMask(), Reaction.RESPECT_SHIELD));
                    assertTrue(ReactionMask.admits(levers.reactionMask(), Reaction.EDGE_DANGER));
                    assertFalse(ReactionMask.admits(levers.reactionMask(), Reaction.HAZARD_PUSH));
                }
            }
        }
        var bench = PressureLadder.bench(4);
        assertEquals(PressureLevers.BENCH.reactionMask(), bench.reactionMask());
        assertEquals(4, bench.reactionDelayTicks());
        assertEquals(0, bench.damagingCastSlackTicks());
        assertEquals(0, bench.localDamagePenaltyMillis());
        assertTrue(bench.damagePreference());
        assertFalse(ReactionMask.admits(PressureLadder.levers(4, 0, 0, true).reactionMask(), Reaction.PUNISH_RECHARGE));
        assertFalse(ReactionMask.admits(PressureLadder.levers(0, 0, 0, false).reactionMask(), Reaction.FINISH));
    }
}
