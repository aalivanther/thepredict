package akyrion.thepredict.adapter.server.goals;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointBudgetTest {
    @Test
    void pathsAndTimeBothCapTheTick() {
        var clock = new AtomicLong(1_000);
        var budget = new WaypointBudget(1, 400, clock::get);
        assertTrue(budget.allow());
        budget.spent(1_000, 1_200);
        assertFalse(budget.allow());
        assertFalse(budget.overrun());
    }

    @Test
    void theCountersStartAgainOnlyWhenTheServerTickChanges() {
        var budget = new WaypointBudget(2, 400, () -> 0L);
        budget.beginTick(10);
        budget.spent(0, 100);
        budget.spent(0, 100);
        assertFalse(budget.allow());
        budget.beginTick(10);
        assertFalse(budget.allow(), "the same tick keeps its spent paths");
        assertEquals(2, budget.paths());
        budget.beginTick(11);
        assertTrue(budget.allow());
        assertEquals(0, budget.paths());
        budget.spent(0, 500_000);
        assertTrue(budget.overrun());
        budget.beginTick(12);
        assertFalse(budget.overrun(), "the microseconds also start again");
    }
}
