package akyrion.thepredict.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** X2 on the frozen bank. The tolerance is the plan's ±0.002 and is not widened. */
class CalibrationFitBaselineTest {
    @Test
    void armDAcceptableMatchesThePublishedPlattFit() throws Exception {
        var figures = CalibrationFitBaseline.armDAcceptable();
        assertEquals(0.070, figures[0], 0.002, "ECE10 " + figures[0]);
        assertEquals(0.112, figures[1], 0.002, "Brier " + figures[1]);
    }
}
