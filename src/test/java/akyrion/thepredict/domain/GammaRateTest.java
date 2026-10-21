package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GammaRateTest {
    @Test
    void thePriorIsOneEventInSixHundredTicks() {
        var rate = new GammaRate();
        assertEquals(10, rate.series());
        rate.observe(0, 20, 1.0);
        assertEquals(310.0, rate.meanGap(0), 1e-9);
    }

    @Test
    void aNegativeGapOrAForgettingOutsideTheHalfOpenUnitIsRejected() {
        var rate = new GammaRate();
        rate.observe(1, 40, 0.5);
        assertThrows(IllegalArgumentException.class, () -> rate.observe(1, -1, 1.0));
        for (var lambda : new double[] {0.0, 1.5, Double.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> rate.observe(1, 20, lambda), "lambda " + lambda);
        }
        assertThrows(IllegalArgumentException.class, () -> rate.observe(GammaRate.SERIES, 20, 1.0));
        assertEquals((GammaRate.PRIOR_TICKS + 40.0) / (GammaRate.PRIOR_EVENTS + 1.0), rate.meanGap(1), 1e-9);
    }
}
