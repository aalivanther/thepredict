package akyrion.thepredict.engine.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A group regime is due on the replan tick only after the plan has lived minPlanTicks. */
class RegimeGroupTest {
    @Test
    void theReplanTickWaitsForTheMinimumPlanAge() {
        assertFalse(GroupRegimeClock.due(20L, 20, 10, 40));
        assertTrue(GroupRegimeClock.due(40L, 20, 40, 40));
        assertFalse(GroupRegimeClock.due(30L, 20, 40, 40));
        assertFalse(GroupRegimeClock.due(40L, 20, 10, 40));
    }
}
