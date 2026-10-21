package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M13MeasurementsTest {
    @Test
    void theTickLayerRowsNameBothCadences() throws Exception {
        var report = new MeasurementReport();
        M20Measurements.write(report);
        var text = report.text();
        assertTrue(text.contains("REACTIVE"));
        assertTrue(text.contains("STANDARD"));
        assertTrue(text.contains("µs"));
        M23Measurements.write(report);
        assertTrue(report.text().contains("Stances and telegraphs (M23)"));
        assertTrue(report.text().contains("| 20 |"));
        assertTrue(report.text().contains("| 100 |"));
        assertTrue(report.text().contains("| 200 |"));
        M24Measurements.write(report);
        assertTrue(report.text().contains("Zone (M24)"));
        assertTrue(report.text().contains("| 1 |"));
        assertTrue(report.text().contains("| 10 |"));
        assertTrue(report.text().contains("| 32 |"));
        assertEquals(7, M24Measurements.fallDamage(10, false, false));
        assertEquals(8, M24Measurements.lavaDamage(20));
    }
}
