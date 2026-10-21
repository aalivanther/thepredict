package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Arm D of the frozen bank (M22.0, X2). */
class EvalCalibrationBaselineTest {
    @Test
    void armDMatchesThePublishedEceAndBrier() throws Exception {
        var figures = EvalCalibrationBaseline.arm("D");
        assertEquals(0.205, figures[0], 0.001, "ECE10 " + figures[0]);
        assertEquals(0.152, figures[1], 0.001, "Brier " + figures[1]);
    }
}
