package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The group step table is published without starting a server. */
class M25MeasurementsTest {
    @Test
    void theGroupStepTableNamesEverySize() {
        var report = new MeasurementReport();
        M25Measurements.write(report);
        akyrion.thepredict.adapter.jev.JevMeasurements.groupColumn(report);
        var text = report.text();
        assertTrue(text.contains("Paso group"));
        assertTrue(text.contains("| 1 | 3 |"));
        assertTrue(text.contains("| 10 | 16 |"));
        assertTrue(text.contains("sin m13Measure"));
        assertTrue(text.contains("Columna grupo"));
        assertTrue(text.contains("REACTIVE"));
        assertTrue(text.contains("STANDARD"));
        System.out.println(text);
    }
}
