package akyrion.thepredict.engine;

import akyrion.thepredict.domain.PressureLadder;
import akyrion.thepredict.domain.PressureLevers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Bench against an entity is the M20 identity plus the profile delay and rung-8 exploit share (M22.7, M26.6). */
class PressureParityTest {
    @Test
    void entityBenchMatchesTheProfileDelayAndAFullMask() {
        for (var delay : new int[] {0, 5, 10, 20}) {
            var bench = PressureLadder.bench(delay);
            assertEquals(new PressureLevers(delay, PressureLevers.BENCH.reactionMask(), 0, true, 0, 2, 6, 0.5), bench);
            assertEquals(bench, PressureLadder.levers(8, delay, 5, false));
        }
    }
}
