package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.M26Measurements;
import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.engine.EnemyModel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternColumnTest {
    @Test
    void thePatternsTableNamesTheColumnAndTheEnemyModelIsLargerThanItsArrays() {
        var report = new MeasurementReport();
        M26Measurements.write(report);
        assertTrue(report.text().contains("## Patterns (M26)"));
        assertTrue(report.text().contains("paso p50"));
        assertTrue(report.text().contains("enemyKinematics p50"));
        assertTrue(report.text().contains("EnemyWatch p50"));
        assertTrue(report.text().contains("no medido"));
        assertTrue(report.text().contains("Columna patrones (hechos 0, 1, 2, 4) caracteres"));
        var zero = JevMeasurements.patternChars(0);
        var one = JevMeasurements.patternChars(1);
        var two = JevMeasurements.patternChars(2);
        var four = JevMeasurements.patternChars(4);
        assertTrue(one > zero && two > one && four > two, zero + " " + one + " " + two + " " + four);
        assertTrue(report.text().contains(zero + ", " + one + ", " + two + ", " + four));
        assertTrue(report.text().contains("tokens " + JevMeasurements.patternTokens(0)));
        System.out.println(report.text());
        assertFalse(JevMeasurements.DETAIL_COLUMNS.contains("patrones"));
        var model = new EnemyModel(new UUID(0, 1), 32, 2);
        assertTrue(model.estimatedBytes() > model.arrayBytes());
        assertTrue(model.arrayBytes() > 0);
    }
}
