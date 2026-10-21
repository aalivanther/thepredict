package akyrion.thepredict;

import akyrion.thepredict.domain.MemoryMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M27MeasurementsTest {
    @Test
    void theMemoryTablePublishesPercentilesAdmissionDigestsDrainAndHeap() {
        var report = new MeasurementReport();
        M27Measurements.write(report);
        var text = report.text();
        assertTrue(text.contains("## Memory (M27)"));
        assertTrue(text.contains("bytes p50"));
        assertTrue(text.contains("bytes p99"));
        assertTrue(text.contains("alta p50 ns"));
        assertTrue(text.contains("digestos ns"));
        assertTrue(text.contains("drenado ns"));
        assertTrue(text.contains("heap bytes"));
        assertTrue(text.contains("| 1 | 1 |"));
        assertTrue(text.contains("| 50 | 5 |"));
        assertTrue(text.contains("| 500 | 10 |"));
        assertTrue(text.contains("cumple") || text.contains("no cumple"));
        assertEquals(MemoryMode.RECORD, MemoryMode.DEFAULT);
    }
}