package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Learning table is measured. A budget miss is text, not a failed test. */
class M28MeasurementsTest {
    @Test
    void theLearningTableNamesTheThreeSessionCounts() {
        var report = new MeasurementReport();
        M28Measurements.write(report);
        var text = report.text();
        System.out.println(text);
        assertTrue(text.contains("## Learning (M28)"));
        assertTrue(text.contains("| 20 |"));
        assertTrue(text.contains("| 100 |"));
        assertTrue(text.contains("| 200 |"));
        assertTrue(text.contains("cumple") || text.contains("no cumple"));
        assertFalse(text.contains("ACCEPT_H1"));
    }
}
