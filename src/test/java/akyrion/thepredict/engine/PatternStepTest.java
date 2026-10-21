package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternStepTest {
    @Test
    void aSpentBudgetKeepsTheEventsForTheNextTick() {
        var step = new PatternStep();
        var id = new UUID(1, 2);
        step.offer(id, 1, 1);
        step.offer(id, 2, 2);
        step.offer(id, 3, 3);
        step.step(4L, 0L);
        assertEquals(3, step.pending());
        assertEquals(1, step.deferred());
        step.step(5L, 1L);
        assertEquals(0, step.pending());
        assertEquals(3, step.drained());
    }
}
