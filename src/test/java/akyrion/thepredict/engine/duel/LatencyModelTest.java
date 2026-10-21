package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyModelTest {
    @Test
    void lognormalStaysNearTheMeasuredPairAndFixedIsExact() {
        var model = LatencyModel.lognormal(20L);
        var millis = new double[100_000];
        for (var index = 0; index < millis.length; index++) {
            millis[index] = model.millis(index);
        }
        Arrays.sort(millis);
        var p50 = millis[millis.length / 2];
        var p95 = millis[(int) (millis.length * 0.95)];
        assertTrue(Math.abs(p50 - 179.0) / 179.0 < 0.03, "p50 " + p50);
        assertTrue(Math.abs(p95 - 228.0) / 228.0 < 0.03, "p95 " + p95);
        assertEquals(model.ticks(3), LatencyModel.lognormal(20L).ticks(3));
        assertEquals(4, LatencyModel.fixed(179).ticks(0));
        assertEquals(0, LatencyModel.fixed(0).ticks(9));
    }
}
