package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The solo rhythm of the live ladder matches v3. The group section only tightens A13. Live bytes are v5
 * (errata E26-9); the frozen v4 file stays under the test resources and is not the production resource.
 */
class LadderV4SoloParityTest {
    private static final int[] DELAY = {20, 18, 16, 14, 12, 10, 8, 6, 0};
    private static final int[] SLACK = {20, 16, 12, 8, 6, 4, 2, 0, 0};
    private static final int[] GROUP_MELEE = {1, 1, 1, 1, 1, 2, 2, 2, 2};
    private static final int[] GROUP_GAP = {20, 16, 14, 12, 10, 10, 8, 6, 6};

    @Test
    void soloLeversMatchV3AndStayAtTheDeliveredWeight() {
        assertTrue(PressureLadder.bundledMatches());
        assertEquals("/difficulty/ladder-v5.json", PressureLadder.RESOURCE);
        assertEquals(0.0, PressureLadder.levers(0, 0, 0, true).exploitShare());
        assertEquals(0.5, PressureLadder.levers(8, 0, 0, true).exploitShare());
        for (var rung = 0; rung < 9; rung++) {
            var solo = PressureLadder.levers(rung, 0, 0, true);
            var expectedDelay = rung == 8 ? 0 : DELAY[rung];
            assertEquals(expectedDelay, solo.reactionDelayTicks(), "delay " + rung);
            assertEquals(SLACK[rung], solo.damagingCastSlackTicks(), "slack " + rung);
            assertEquals(2, solo.maxMeleeAttackers());
            assertEquals(6, solo.impactGapTicks());
            var group = PressureLadder.groupLevers(rung, 0, 0, true);
            assertEquals(GROUP_MELEE[rung], group.maxMeleeAttackers());
            assertEquals(GROUP_GAP[rung], group.impactGapTicks());
            assertTrue(group.maxMeleeAttackers() <= GroupFairnessRules.MELEE_WEIGHT);
            assertTrue(group.impactGapTicks() >= 4);
            if (rung > 0) {
                assertTrue(group.maxMeleeAttackers() >= PressureLadder.groupMelee(rung - 1));
                assertTrue(group.impactGapTicks() <= PressureLadder.groupGap(rung - 1));
            }
        }
    }
}
