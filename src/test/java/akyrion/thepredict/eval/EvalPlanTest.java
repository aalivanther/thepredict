package akyrion.thepredict.eval;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.engine.EngineSettings;
import akyrion.thepredict.engine.RequestBudget;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pair n + 1 is not sent and the spend cap uses the token price (M22.4, W7). */
class EvalPlanTest {
    @Test
    void thePlanStopsAtNAndFourReserveChecksRefuse() {
        var plan = new EvalPlan(3);
        assertTrue(plan.maySend(0));
        assertTrue(plan.maySend(2));
        assertFalse(plan.maySend(3));
        assertThrows(IllegalArgumentException.class, () -> new EvalPlan(0));
        assertThrows(IllegalArgumentException.class, () -> new EvalPlan(801));
        var budget = new RequestBudget(new EngineSettings(600, 300, 8, 4, 10_000_000, 10, 2, 64, 16, 64, 2000,
                800_000_000L));
        var tokens = budget.tokensFor(1500);
        var estimate = tokens * RequestBudget.USD_PER_MILLION_INPUT_TOKENS / 1_000_000.0;
        assertTrue(Math.abs(estimate - EvalPlan.estimateUsd(1500)) / estimate < 0.10);
        var tight = new RequestBudget(new EngineSettings(1, 1, 1, 1, 5, 10, 2, 64, 16, 64, 2000, 800_000_000L));
        var world = UUID.randomUUID();
        assertFalse(tight.tryReserve(world, new EncounterId(1), 6, 0L));
        assertTrue(tight.tryReserve(world, new EncounterId(1), 1, 0L));
        assertFalse(tight.tryReserve(world, new EncounterId(1), 1, 0L));
        assertFalse(tight.tryReserve(world, new EncounterId(2), 1, 0L));
        assertFalse(EvalPlan.withinCap(0.9, 0.2, 1.0));
        assertFalse(EvalPlan.withinCap(-1.0, 0.0, 1.0));
    }
}
