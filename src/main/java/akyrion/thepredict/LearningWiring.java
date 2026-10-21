package akyrion.thepredict;

import akyrion.thepredict.config.StorageSettings;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.distill.DistillArtifactFormat;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.PackagedSource;
import akyrion.thepredict.domain.learn.ArtifactFormat;
import akyrion.thepredict.domain.learn.CalibratedMinimum;
import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.CalibrationTarget;
import akyrion.thepredict.domain.learn.OptionsBand;
import akyrion.thepredict.domain.learn.QuestionKind;
import akyrion.thepredict.engine.GateVerdicts;
import akyrion.thepredict.engine.LearnPath;
import akyrion.thepredict.engine.distill.DistillVerdict;
import akyrion.thepredict.engine.learn.CalibrationStartup;
import akyrion.thepredict.engine.learn.ConfidenceCalibrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The learned files the root reads (M28, M29), all under one {@code learn} folder: the sibling of
 * {@code records.directory}, the same one the storage writer, {@code learn} and {@code distill} use, so a nested
 * {@code records.directory} never splits them (CAR-10). A file that cannot be read or is malformed never stops the
 * start: it leaves the safe state (ceiling {@code off}, identity calibrator, no packaged source) with a warning (A6).
 */
final class LearningWiring {
    private final Path learn;
    private final Consumer<String> warnings;

    /**
     * Creates the wiring over one folder.
     *
     * @param learn the {@code learn} folder
     * @param warnings receives one line per file that was not used
     */
    LearningWiring(Path learn, Consumer<String> warnings) {
        this.learn = Objects.requireNonNull(learn, "learn");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * The {@code learn} folder of a data folder: next to the records directory, as {@code LearnAdmin} and
     * {@code DistillAdmin} compute it.
     *
     * @param dataFolder the plugin data folder
     * @param storage the storage settings in use
     * @return the folder
     */
    static Path folder(Path dataFolder, StorageSettings storage) {
        return dataFolder.resolve(storage.directory()).resolveSibling("learn");
    }

    /**
     * The active value artifact, whose missing verdict the start warns about.
     *
     * @return {@code learn/active.json}
     */
    Path valueArtifact() {
        return learn.resolve("active.json");
    }

    /**
     * The active calibration artifact.
     *
     * @return {@code learn/active.tplc}
     */
    Path calibrationArtifact() {
        return learn.resolve("active.tplc");
    }

    /**
     * The ceiling of the distilled artifact in force: the status of {@code learn/distill/verdict.json} when it names the
     * SHA-256 of the {@code .tpld} that {@code learn/distill/active.txt} names (LD-09). Without an artifact, or with
     * one above {@code maxBytes}, the ceiling is {@code off}.
     *
     * @param maxBytes {@code distill.maxArtifactBytes}
     * @return the ceiling
     */
    DistillMode distillCeiling(int maxBytes) {
        var distill = learn.resolve("distill");
        try {
            var active = distill.resolve("active.txt");
            if (!Files.isRegularFile(active)) {
                return DistillMode.OFF;
            }
            var name = Files.readString(active).trim();
            LearnPath.requireName(name);
            var artifact = distill.resolve("artifacts").resolve(name);
            if (!Files.isRegularFile(artifact) || Files.size(artifact) > maxBytes) {
                warnings.accept("Distill verdict was not loaded: the artifact named by active.txt is missing or larger"
                        + " than distill.maxArtifactBytes");
                return DistillMode.OFF;
            }
            var sha = DistillArtifactFormat.sha256(Files.readAllBytes(artifact));
            return DistillVerdict.load(distill.resolve("verdict.json"), sha);
        } catch (IOException | RuntimeException exception) {
            warnings.accept("Distill verdict was not loaded: " + exception.getClass().getSimpleName());
            return DistillMode.OFF;
        }
    }

    /**
     * The packaged distilled sources of the plugin JAR. A broken resource, or one that names a Jev source, loads none.
     *
     * @param loader the class loader of the plugin
     * @return the sources, empty on failure
     */
    List<PackagedSource.Loaded> packaged(ClassLoader loader) {
        try {
            return PackagedSource.load(loader);
        } catch (IOException | RuntimeException exception) {
            warnings.accept("Packaged distill was not loaded: " + exception.getClass().getSimpleName());
            return List.of();
        }
    }

    /**
     * The calibration artifact, installed only when {@code gates} holds a {@code PASS} for its SHA-256 (W8, LD-17); the
     * identity otherwise, also while the verdicts are still pending.
     *
     * @param model the live Jev model
     * @param key the key of the calibrated gate
     * @param gates the loaded verdicts
     * @return the installation
     */
    CalibrationStartup.Installed calibrator(String model, CalibrationKey key, GateVerdicts gates) {
        var file = calibrationArtifact();
        try {
            var verified = Files.isRegularFile(file)
                    && gates.status(ArtifactFormat.sha256(Files.readAllBytes(file))) == GateVerdicts.Status.PASS;
            return CalibrationStartup.install(file, model, key, verified);
        } catch (IOException | RuntimeException exception) {
            warnings.accept("Calibration artifact was not loaded: " + exception.getClass().getSimpleName());
            return new CalibrationStartup.Installed(ConfidenceCalibrator.identity(model), Double.NaN);
        }
    }

    /**
     * The key of the calibrated gate for the configured question detail (CAR-20, M28.4).
     *
     * @param model the live Jev model
     * @param detail {@code decision.questionDetail}
     * @return the key
     */
    static CalibrationKey calibrationKey(String model, QuestionDetail detail) {
        return new CalibrationKey(model, detail, QuestionKind.NOW, CalibrationTarget.ACCEPTABLE, OptionsBand.TWO);
    }

    /**
     * The calibrated floor {@code p*} the gate uses: {@code NaN} for {@code parity}, otherwise the configured value of
     * {@code decision.minimumCalibratedConfidence} (LD-17).
     *
     * @param minimum the configured minimum
     * @return the target
     */
    static double calibrationTarget(CalibratedMinimum minimum) {
        return minimum.parity() ? Double.NaN : minimum.explicit();
    }
}
