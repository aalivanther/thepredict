package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reports are chunked and a second one is rejected (M22.11). */
class EvalReportTaskTest {
    @Test
    void chunksStayUnderTheCapAndABusyReportIsRejected() {
        var text = "a".repeat(50);
        var parts = EvalReportTask.chunks(text, 20);
        assertEquals(3, parts.size());
        assertEquals(20, parts.get(0).length());
        assertEquals(10, parts.get(2).length());
        EvalReportTask.admit(false, "reports/day.md");
        assertThrows(IllegalStateException.class, () -> EvalReportTask.admit(true, "reports/day.md"));
        assertThrows(IllegalArgumentException.class, () -> EvalReportTask.admit(false, "../day.md"));
    }
}
