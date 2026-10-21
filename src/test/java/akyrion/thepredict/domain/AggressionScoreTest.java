package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The terms of the melee-token score of M25.3: role, gap, sight, holder and the plan's main attacker. The octant is
 * the folded one of {@link TickSignals}: 0 in front of the target's look and 4 behind it.
 */
class AggressionScoreTest {
    @Test
    void aMemberThatCannotHitScoresZeroWhateverElseHolds() {
        assertEquals(0, AggressionScore.of(GroupRole.PRESSURE, 0, 0, false, true, true));
    }

    @Test
    void theRoleAddsItsPoints() {
        assertEquals(300, base(GroupRole.PRESSURE));
        assertEquals(250, base(GroupRole.FLANK));
        assertEquals(200, base(GroupRole.CONTROL));
        assertEquals(200, base(GroupRole.RANGED));
        assertEquals(100, base(GroupRole.GUARD));
        assertEquals(50, base(GroupRole.SUPPORT));
        assertEquals(0, base(GroupRole.RESERVE));
        assertEquals(0, AggressionScore.of(null, 8, 2, true, false, false), "no role scores like the reserve");
    }

    @Test
    void theGapTermFallsFromTwoHundredToZeroAtTwoBlocks() {
        assertEquals(200, AggressionScore.of(GroupRole.RESERVE, 0, 2, true, false, false));
        assertEquals(200, AggressionScore.of(GroupRole.RESERVE, -3, 2, true, false, false));
        assertEquals(100, AggressionScore.of(GroupRole.RESERVE, 4, 2, true, false, false));
        assertEquals(25, AggressionScore.of(GroupRole.RESERVE, 7, 2, true, false, false));
        assertEquals(0, AggressionScore.of(GroupRole.RESERVE, 8, 2, true, false, false));
        assertEquals(0, AggressionScore.of(GroupRole.RESERVE, 40, 2, true, false, false));
    }

    @Test
    void sightRewardsTheFrontOctantsAndPunishesOnlyTheOneBehind() {
        assertEquals(150, sight(0));
        assertEquals(150, sight(1));
        assertEquals(0, sight(2));
        assertEquals(0, sight(3));
        assertEquals(-150, sight(4));
    }

    @Test
    void theHolderAndTheNamedAttackerAddTheirBonuses() {
        assertEquals(150, AggressionScore.of(GroupRole.RESERVE, 8, 2, true, true, false));
        assertEquals(100, AggressionScore.of(GroupRole.RESERVE, 8, 2, true, false, true));
        assertEquals(300 + 200 + 150 + 150 + 100, AggressionScore.of(GroupRole.PRESSURE, 0, 0, true, true, true));
    }

    private static int base(GroupRole role) {
        return AggressionScore.of(role, 8, 2, true, false, false);
    }

    private static int sight(int octant) {
        return AggressionScore.of(GroupRole.RESERVE, 8, octant, true, false, false);
    }
}
