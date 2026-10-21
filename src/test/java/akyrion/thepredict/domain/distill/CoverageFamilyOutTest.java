package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A covered cell does not cover another family. */
class CoverageFamilyOutTest {
    @Test
    void aSiblingContextIsUnseen() {
        var cell = new CoverageCell("family-a", 50, 0.0, 0.0, List.of("press"), false, 1.0);
        var index = CoverageIndex.of(List.of(cell), 0L, "finger", "outcome", true);
        assertTrue(index.covered("family-a", List.of("press"), 0L, 60, "finger", "outcome", 50, 0.02).isEmpty());
        assertEquals(OodCause.OOD_CELL_UNSEEN,
                index.covered("family-b", List.of("press"), 0L, 60, "finger", "outcome", 50, 0.02).orElseThrow());
    }
}
