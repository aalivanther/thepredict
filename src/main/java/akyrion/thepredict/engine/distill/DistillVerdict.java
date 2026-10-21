package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.JsonFields;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Highest mode a recorded verdict allows (M29.7, rule W8). A missing file stays off. A tape can reach autonomous.
 * G0 alone stays in shadow. A verdict is per artifact: the top-level {@code sha256} of {@code verdict.json} must name
 * the SHA-256 of the distilled artifact in force, so a verdict never validates another artifact. Only top-level
 * fields count; a {@code status} or {@code tape} inside a nested object is ignored.
 */
public final class DistillVerdict {
    private DistillVerdict() {
    }

    /**
     * The ceiling of one status token.
     *
     * @param status verdict status, blank when there is none
     * @param tape whether a champion tape passed
     * @return the ceiling
     */
    public static DistillMode ceiling(String status, boolean tape) {
        Objects.requireNonNull(status, "status");
        var token = status.trim();
        if (token.isEmpty()) {
            return DistillMode.OFF;
        }
        if ("G0".equals(token) || "G0_ONLY".equals(token)) {
            return DistillMode.SHADOW;
        }
        if ("PASS_SIM_ONLY".equals(token)) {
            return DistillMode.ASSIST;
        }
        if ("PASS".equals(token)) {
            return tape ? DistillMode.AUTONOMOUS : DistillMode.ASSIST;
        }
        return DistillMode.OFF;
    }

    /**
     * Reads {@code verdict.json} for one artifact. A missing or malformed file, or one that names another artifact,
     * is off.
     *
     * @param file the verdict file
     * @param artifactSha256 SHA-256 of the distilled artifact in force, empty when none is published
     * @return the ceiling
     * @throws IOException when the file cannot be read
     */
    public static DistillMode load(Path file, String artifactSha256) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(artifactSha256, "artifactSha256");
        if (artifactSha256.isEmpty() || !Files.isRegularFile(file)) {
            return DistillMode.OFF;
        }
        try {
            var fields = JsonFields.topLevel(Files.readString(file));
            if (!artifactSha256.equals(fields.get("sha256"))) {
                return DistillMode.OFF;
            }
            return ceiling(fields.getOrDefault("status", ""), "true".equals(fields.get("tape")));
        } catch (IllegalArgumentException malformed) {
            return DistillMode.OFF;
        }
    }

    /**
     * Legacy entry point of a caller that does not know which artifact is in force. Without an artifact fingerprint
     * no verdict applies, so the ceiling is off.
     *
     * @param file the verdict file
     * @return off
     * @throws IOException when the file cannot be read
     */
    public static DistillMode load(Path file) throws IOException {
        return load(file, "");
    }
}
