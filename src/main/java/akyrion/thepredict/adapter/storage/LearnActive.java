package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code learn/active.json} (M28.9). The writer replaces it with {@link AtomicFiles}: a forced temporary file and an
 * atomic move.
 */
final class LearnActive {
    private final String value;
    private final String calibration;
    private final String previousValue;
    private final String previousCalibration;

    private LearnActive(String value, String calibration, String previousValue, String previousCalibration) {
        this.value = value;
        this.calibration = calibration;
        this.previousValue = previousValue;
        this.previousCalibration = previousCalibration;
    }

    static LearnActive empty() {
        return new LearnActive("", "", "", "");
    }

    String value() {
        return value;
    }

    String previousValue() {
        return previousValue;
    }

    LearnActive withValue(String sha) {
        return new LearnActive(sha, calibration, value, previousCalibration);
    }

    LearnActive swapped() {
        return new LearnActive(previousValue, previousCalibration, value, calibration);
    }

    LearnActive cleared(String kind) {
        var nextValue = "calibration".equals(kind) ? value : "";
        var nextCalibration = "value".equals(kind) ? calibration : "";
        return new LearnActive(nextValue, nextCalibration, previousValue, previousCalibration);
    }

    static LearnActive read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return empty();
        }
        var text = Files.readString(file);
        return new LearnActive(field(text, "value"), field(text, "calibration"), field(text, "previousValue"),
                field(text, "previousCalibration"));
    }

    void write(Path file) throws IOException {
        AtomicFiles.write(file, json());
    }

    private String json() {
        return "{\"value\":\"" + value + "\",\"calibration\":\"" + calibration + "\",\"previousValue\":\""
                + previousValue + "\",\"previousCalibration\":\"" + previousCalibration + "\"}";
    }

    private static String field(String text, String name) {
        var marker = "\"" + name + "\":\"";
        var start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        var from = start + marker.length();
        var end = text.indexOf('"', from);
        return end < 0 ? "" : text.substring(from, end);
    }
}
