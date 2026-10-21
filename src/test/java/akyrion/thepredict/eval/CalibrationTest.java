package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECE on a perfect sample and the Murphy identity (M22.6). */
class CalibrationTest {
    @Test
    void aPerfectSampleHasASmallEceAndMurphySumsTheBrier() {
        var n = 10_000;
        var confidence = new double[n];
        var success = new int[n];
        for (var index = 0; index < n; index++) {
            var low = index < n / 2;
            confidence[index] = low ? 0.2 : 0.8;
            var slot = index % 5;
            success[index] = low ? (slot == 0 ? 1 : 0) : (slot == 0 ? 0 : 1);
        }
        var ece = Calibration.ece(confidence, success, 10);
        assertTrue(ece <= 0.02, "ece " + ece);
        var murphy = Calibration.murphy(confidence, success);
        var brier = Calibration.brier(confidence, success);
        assertEquals(brier, murphy[3], 1e-9);
        var svg = Calibration.svg(new double[][] {{0.2, 0.2, 1}, {0.8, 0.8, 1}});
        assertTrue(svg.startsWith("<svg"));
        assertTrue(!svg.contains("<script"));
    }
}
