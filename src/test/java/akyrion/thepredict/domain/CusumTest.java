package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CusumTest {
    @Test
    void failuresAgainstAHighBaselineAlarmAndResetTheStatistic() {
        var cusum = new Cusum();
        assertFalse(cusum.observe(false, 0.5, 4.0));
        cusum.arm(0.8);
        var alarm = false;
        for (var trial = 0; trial < 30 && !alarm; trial++) {
            alarm = cusum.observe(false, 0.5, 4.0);
        }
        assertTrue(alarm);
        assertEquals(0.0, cusum.statistic(), 1e-12);
    }
}
