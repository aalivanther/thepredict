package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Two builds of the same outcome rows share a path. */
class DistillMethodsBaselineTest {
    @Test
    void thePathIsStable() {
        var rows = new TreeRows[20];
        for (var index = 0; index < rows.length; index++) {
            rows[index] = new TreeRows(new int[] {index % 4, 0, 0, 0, 0, 0, 0}, index % 2, 1.0);
        }
        var bands = new int[] {0, 0, 0, 0, 0, 0, 0};
        assertEquals(DistillMethodsBaseline.path(rows, bands), DistillMethodsBaseline.path(rows, bands));
    }
}
