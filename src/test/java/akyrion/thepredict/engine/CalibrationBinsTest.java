package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Twenty bins, and only a Jev window counts (M22.6). */
class CalibrationBinsTest {
    @Test
    void binsStayInsideTwentyAndLocalDoesNotCount() {
        assertEquals(0, CalibrationBins.bin(0.0));
        assertEquals(19, CalibrationBins.bin(0.999));
        assertEquals(19, CalibrationBins.bin(1.0));
        assertEquals(16, CalibrationBins.bin(0.8));
        assertTrue(CalibrationBins.counts(ControlLevel.JEV));
        assertFalse(CalibrationBins.counts(ControlLevel.LOCAL));
        assertFalse(CalibrationBins.counts(ControlLevel.ALTERNATIVE));
        assertEquals(20, CalibrationBins.COUNT);
    }
}
