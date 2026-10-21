package akyrion.thepredict.eval;

import akyrion.thepredict.M22Measurements;
import akyrion.thepredict.MeasurementReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writes the local Difficulty (M22) rows. The full {@code m13Measure} task also calls the same method. */
class DifficultyCostTest {
    @Test
    void theTableIsWritten() throws Exception {
        var report = new MeasurementReport();
        M22Measurements.write(report);
        var path = Path.of("build/reports/m22/difficulty-cost.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report.text());
        assertTrue(report.text().contains("Difficulty (M22)"));
        assertTrue(report.text().contains("200"));
    }
}
