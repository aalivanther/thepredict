package akyrion.thepredict.domain.learn;

import java.util.List;

/**
 * What an artifact header records (M28.9, rule C23). The day is an argument; the body has no wall clock.
 * Fingerprint keys that contain a path, a directory, a file, an API key or a URL are excluded (errata E28-15 applied),
 * including {@code records.directory}, {@code storage.file}, {@code jev.apiKey}, {@code jev.apiKeyEnvironment} and
 * {@code jev.baseUrl}. {@code learning.*} input and output paths are excluded the same way. {@code jev.model} and
 * {@code questionDetail} stay in the calibrator fingerprint.
 */
public final class ArtifactHeader {
    /** Value magic. */
    public static final String VALUE_MAGIC = "TPLV";
    /** Calibrator magic. */
    public static final String CALIBRATION_MAGIC = "TPLC";
    /** Format version. */
    public static final int FORMAT = 1;
    /** Keys left out of every fingerprint. */
    public static final List<String> EXCLUDED = List.of("records.directory", "storage.file", "jev.apiKey",
            "jev.apiKeyEnvironment", "jev.baseUrl");

    private ArtifactHeader() {
    }

    /**
     * Whether a config path is hashed.
     *
     * @param path dotted path
     * @return {@code false} for paths, files, keys and URLs
     */
    public static boolean included(String path) {
        var lower = path.toLowerCase(java.util.Locale.ROOT);
        if (EXCLUDED.contains(path)) {
            return false;
        }
        return !lower.contains("path") && !lower.contains("directory") && !lower.contains("file")
                && !lower.contains("apikey") && !lower.contains("url");
    }
}
