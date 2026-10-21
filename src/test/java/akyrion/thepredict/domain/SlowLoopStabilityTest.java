package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A gain of 2.2 stays on the ladder only with the step limit (M22.8, B3). */
class SlowLoopStabilityTest {
    @Test
    void lambdaTwoPointTwoDivergesWithoutTheClampAndStaysBoundedWithIt() {
        var open = 8.0;
        var clamped = 8.0;
        for (var fight = 0; fight < 12; fight++) {
            var logTtk = Math.log(30.0);
            open += DifficultyPolicy.deltaU(logTtk, Math.log(60.0), 0.0, 2.2, 0.12, false);
            clamped = DifficultyPolicy.nextU(clamped,
                    DifficultyPolicy.deltaU(logTtk, Math.log(60.0), 0.0, 2.2, 0.12, true), 0, 8);
        }
        assertTrue(open < 0.0 || open > 8.0);
        assertTrue(clamped >= 0.0 && clamped <= 8.0);
        assertTrue(clamped < 8.0);
    }
}
