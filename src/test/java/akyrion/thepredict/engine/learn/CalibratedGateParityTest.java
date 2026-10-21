package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.learn.ArtifactFormat;
import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.CalibrationTarget;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.OptionsBand;
import akyrion.thepredict.domain.learn.PlattMath;
import akyrion.thepredict.domain.learn.QuestionKind;
import akyrion.thepredict.learn.CalibrationFitBaseline;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Errata E28-6 applied: parity is per profile and the artifact is a real {@code .tplc}. */
class CalibratedGateParityTest {
    @Test
    void armDResponsesMatchTheProfileFloorLoadedFromTheArtifact() throws Exception {
        var rows = CalibrationFitBaseline.samples();
        assertEquals(96, rows.size());
        var directory = Files.createTempDirectory("m28-tplc");
        var path = directory.resolve("active.tplc");
        Files.write(path, ArtifactFormat.calibrationArtifact(1, "jev-1.13.0", 833_000, 1_417_000));
        var key = new CalibrationKey("jev-1.13.0", QuestionDetail.FULL, QuestionKind.NOW, CalibrationTarget.ACCEPTABLE,
                OptionsBand.TWO);
        var installed = CalibrationStartup.install(path, "jev-1.13.0", key, true);
        assertTrue(Double.isNaN(installed.target()));
        var unverified = CalibrationStartup.install(path, "jev-1.13.0", key);
        assertEquals(0.45, unverified.calibrator().apply(key, "jev-1.13.0", 0.45), 1.0e-12);
        assertEquals(0, unverified.calibrator().size());
        var published = PlattMath.apply(0.45, 833_000 / 1_000_000.0, 1_417_000 / 1_000_000.0);
        assertEquals(published, installed.calibrator().apply(key, "jev-1.13.0", 0.45), 1.0e-12);
        assertTrue(published > 0.70);
        assertNotEquals(0.45, published, 1.0e-6);
        var learning = new SessionLearning();
        learning.useCalibrator(ConfidenceGate.CALIBRATED, installed.calibrator(), key, "jev-1.13.0", installed.target());
        var straddling = 0;
        for (var row : rows) {
            if ((row.confidence() >= 0.45) != (row.confidence() >= 0.70)) {
                straddling++;
            }
        }
        assertTrue(straddling > 0);
        for (var profileFloor : new double[] {0.45, 0.70, 0.90}) {
            var star = learning.confidenceFloor(profileFloor);
            assertEquals(profileFloor, star, 1.0e-6);
            for (var row : rows) {
                assertEquals(row.confidence() >= profileFloor, row.confidence() >= star,
                        row.scenario() + " " + profileFloor);
            }
        }
        assertNotEquals(0.45, learning.confidenceFloor(0.70), 1.0e-6);
        var identity = CalibrationStartup.install(directory.resolve("missing.tplc"), "jev-1.13.0", key);
        assertTrue(Double.isNaN(identity.target()));
        var rawFloor = new SessionLearning();
        rawFloor.useCalibrator(ConfidenceGate.CALIBRATED, identity.calibrator(), key, "jev-1.13.0", identity.target());
        assertEquals(0.62, rawFloor.confidenceFloor(0.62), 0.0);
        assertEquals(0.90, rawFloor.confidenceFloor(0.90), 0.0);
        var worse = new PlattMath.Fit(-2.0, 0.0, false, java.util.Optional.empty());
        var rejected = ConfidenceCalibrator.of("jev-1.13.0", key, worse, new double[] {0.9, 0.1}, new int[] {1, 0});
        assertEquals(0.8, rejected.apply(key, "jev-1.13.0", 0.8), 1.0e-9);
        var stale = installed.calibrator().threshold(key, "other-model", 0.70, true, 0.90);
        assertEquals(0.70, stale.rawThreshold(), 0.0);
        assertEquals(java.util.Optional.of(LearnCause.CALIBRATION_STALE), stale.cause());
        assertFalse(learning.confidenceFloor(0.90) == 0.45);
    }

    @Test
    void parityKeepsTheFloorEvenWithABiasThatSaturatesTheLogit() throws Exception {
        var key = new CalibrationKey("jev-1.13.0", QuestionDetail.FULL, QuestionKind.NOW, CalibrationTarget.ACCEPTABLE,
                OptionsBand.TWO);
        var directory = Files.createTempDirectory("m28-tplc-bias");
        var path = directory.resolve("active.tplc");
        Files.write(path, ArtifactFormat.calibrationArtifact(1, "jev-1.13.0", 833_000, 10_000_000));
        var installed = CalibrationStartup.install(path, "jev-1.13.0", key, true);
        assertTrue(installed.calibrator().apply(key, "jev-1.13.0", 0.45) > 0.999);
        var learning = new SessionLearning();
        learning.useCalibrator(ConfidenceGate.CALIBRATED, installed.calibrator(), key, "jev-1.13.0", installed.target());
        assertEquals(0.45, learning.confidenceFloor(0.45), 0.0);
        assertEquals(0.62, learning.confidenceFloor(0.62), 0.0);
    }

    @Test
    void aFlatOrDecreasingSlopeFromTheFileIsTheIdentity() throws Exception {
        var key = new CalibrationKey("jev-1.13.0", QuestionDetail.FULL, QuestionKind.NOW, CalibrationTarget.ACCEPTABLE,
                OptionsBand.TWO);
        var path = Files.createTempDirectory("m28-tplc-flat").resolve("active.tplc");
        Files.write(path, ArtifactFormat.calibrationArtifact(1, "jev-1.13.0", -2_000_000, 0));
        var installed = CalibrationStartup.install(path, "jev-1.13.0", key, true);
        assertEquals(0.3, installed.calibrator().apply(key, "jev-1.13.0", 0.3), 1.0e-12);
        assertEquals(java.util.Optional.of(LearnCause.CALIBRATION_NON_MONOTONE),
                installed.calibrator().threshold(key, "jev-1.13.0", 0.5, false, 0.9).cause());
    }
}
