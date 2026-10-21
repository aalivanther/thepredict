package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlattMathTest {
    @Test
    void emptyAndShortSamplesStayTheIdentityAndAFlatSlopeIsRejected() {
        var empty = PlattMath.map(new double[0], new int[0], 20, 48, 24, 0);
        assertTrue(empty.identity());
        assertEquals(1.0, empty.slope());
        var shortSample = new double[10];
        var labels = new int[10];
        java.util.Arrays.fill(shortSample, 0.8);
        var insufficient = PlattMath.map(shortSample, labels, 20, 48, 24, 10);
        assertEquals(java.util.Optional.of(LearnCause.CALIBRATION_INSUFFICIENT), insufficient.cause());
        assertEquals(0.45, PlattMath.rawThreshold(0.45, 1.0, 0.0, 0.45), 1.0e-12);
        assertEquals(java.util.Optional.of(LearnCause.CALIBRATION_NON_MONOTONE),
                PlattMath.map(inverted(), invertedLabels(), 0, 48, 24, 24).cause());
        var confidence = new double[] {0.9, 0.9, 0.1, 0.1};
        var success = new int[] {1, 1, 0, 0};
        var worse = PlattMath.retain(new PlattMath.Fit(-2.0, 0.0, false, java.util.Optional.empty()), confidence,
                success);
        assertTrue(worse.identity());
        assertEquals(java.util.Optional.of(LearnCause.CALIBRATION_NON_MONOTONE), worse.cause());
    }

    @Test
    void fiveFoldsRejectAFitThatOnlyWinsOnTheSameRows() {
        var confidence = new double[260];
        var success = new int[260];
        var scenario = new int[260];
        var cursor = 0;
        for (var row = 0; row < 50; row++) {
            confidence[cursor] = 0.85;
            success[cursor] = 0;
            scenario[cursor] = 0;
            cursor++;
            confidence[cursor] = 0.15;
            success[cursor] = 1;
            scenario[cursor] = 0;
            cursor++;
        }
        for (var place = 1; place <= 4; place++) {
            for (var row = 0; row < 20; row++) {
                confidence[cursor] = 0.85;
                success[cursor] = 1;
                scenario[cursor] = place;
                cursor++;
                confidence[cursor] = 0.15;
                success[cursor] = 0;
                scenario[cursor] = place;
                cursor++;
            }
        }
        var inSample = PlattMath.map(confidence, success, 0, 0, 0, 5);
        assertTrue(inSample.slope() > PlattMath.SLOPE_FLOOR);
        assertTrue(PlattMath.brier(confidence, success, inSample)
                < PlattMath.brier(confidence, success, PlattMath.Fit.IDENTITY));
        var fit = PlattMath.fit(confidence, success, scenario, 0, 0, 0);
        assertTrue(fit.identity());
        assertEquals(java.util.Optional.of(LearnCause.CALIBRATION_NOT_BETTER), fit.cause(),
                "the slope is monotone; it only fails to beat the identity out of sample");
    }

    @Test
    void aFoldCountsItsScenariosNotItsFolds() {
        var scenario = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        var fold = PlattFolds.folds(scenario);
        assertEquals(0, fold[0]);
        assertEquals(0, fold[5]);
        var confidence = new double[scenario.length];
        var success = new int[scenario.length];
        var train = PlattFolds.slice(confidence, success, scenario, fold, 0, true);
        assertEquals(8, train.confidence().length);
        assertEquals(8, train.scenarios());
        var test = PlattFolds.slice(confidence, success, scenario, fold, 0, false);
        assertEquals(2, test.scenarios());
    }

    private static double[] inverted() {
        var confidence = new double[48];
        for (var index = 0; index < 48; index++) {
            confidence[index] = index < 24 ? 0.9 : 0.1;
        }
        return confidence;
    }

    private static int[] invertedLabels() {
        var success = new int[48];
        for (var index = 24; index < 48; index++) {
            success[index] = 1;
        }
        return success;
    }
}
