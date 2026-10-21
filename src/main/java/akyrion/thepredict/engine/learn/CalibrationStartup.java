package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.PlattMath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Installs the calibrator at startup (M28.4, errata E28-6 applied). {@code c*} is not stored: each profile supplies
 * its own {@code minimumConfidence}. A missing file installs the identity. A file acts only with a passing verdict
 * for its SHA-256 (rule W8): without one the identity is installed, so nothing learned moves the gate. A slope at or
 * below {@link PlattMath#SLOPE_FLOOR} is the identity too (errata E28-5).
 */
public final class CalibrationStartup {
    private CalibrationStartup() {
    }

    /**
     * What startup installs. {@code target} is NaN so parity uses the profile floor.
     *
     * @param calibrator the calibrator
     * @param target parity marker, always NaN
     */
    public record Installed(ConfidenceCalibrator calibrator, double target) {
    }

    /**
     * Legacy entry point of a caller that does not check the verdict. It installs the identity: an unverified
     * calibrator never acts.
     *
     * @param file candidate artifact
     * @param model live model
     * @param key calibration key the artifact is installed under
     * @return the identity installation
     * @throws IOException when the file cannot be read
     */
    public static Installed install(Path file, String model, CalibrationKey key) throws IOException {
        return install(file, model, key, false);
    }

    /**
     * Loads {@code file} when it is a real {@code .tplc} and its verdict passed. Otherwise the identity.
     *
     * @param file candidate artifact
     * @param model live model, used when the file is absent
     * @param key calibration key the artifact is installed under
     * @param verified whether {@code GateVerdicts} holds a passing verdict for the SHA-256 of {@code file}
     * @return the installation
     * @throws IOException when the file cannot be read
     */
    public static Installed install(Path file, String model, CalibrationKey key, boolean verified) throws IOException {
        var loaded = CalibrationFiles.load(file);
        if (loaded.isEmpty() || !verified) {
            return new Installed(ConfidenceCalibrator.identity(model), Double.NaN);
        }
        var artifact = loaded.get();
        var slope = artifact.slopeMillionths() / 1_000_000.0;
        var bias = artifact.biasMillionths() / 1_000_000.0;
        var identity = Math.abs(slope - 1.0) < 1.0e-9 && Math.abs(bias) < 1.0e-9;
        var fit = identity ? PlattMath.Fit.IDENTITY : new PlattMath.Fit(slope, bias, false, Optional.empty());
        if (!identity && slope <= PlattMath.SLOPE_FLOOR) {
            fit = new PlattMath.Fit(1.0, 0.0, true, Optional.of(LearnCause.CALIBRATION_NON_MONOTONE));
        }
        var calibrator = new ConfidenceCalibrator(artifact.model(), new CalibrationKey[] {key},
                new PlattMath.Fit[] {fit});
        return new Installed(calibrator, Double.NaN);
    }
}
