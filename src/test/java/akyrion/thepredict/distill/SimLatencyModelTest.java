package akyrion.thepredict.distill;

import akyrion.thepredict.engine.duel.LatencyModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Jev is 5 or 6 ticks in the published band. The student is 1. The 179 ms model stays at 4 ticks. */
class SimLatencyModelTest {
    @Test
    void theBandsStayPublished() {
        assertEquals(5, SimLatencyModel.jevTicks(250));
        assertEquals(6, SimLatencyModel.jevTicks(251));
        assertEquals(6, SimLatencyModel.jevTicks(300));
        assertEquals(1, SimLatencyModel.distilledTicks());
        assertEquals(4, LatencyModel.fixed(179).ticks(0));
    }
}
