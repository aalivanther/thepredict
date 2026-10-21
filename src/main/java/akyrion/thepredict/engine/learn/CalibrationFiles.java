package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.ArtifactFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads a {@code .tplc} written by {@link ArtifactFormat#calibrationArtifact}.
 */
public final class CalibrationFiles {
    private CalibrationFiles() {
    }

    /**
     * Reads a calibration file. A missing file is empty.
     *
     * @param file the artifact
     * @return the parsed file
     * @throws IOException when the file cannot be read
     */
    public static Optional<ArtifactFormat.CalibrationArtifact> load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(ArtifactFormat.readCalibration(Files.readAllBytes(file)));
    }
}
