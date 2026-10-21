package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coverage uses the outcome lower bound. Agreement never covers a cell. Errata E29-1 applied. */
class CoverageNonInferiorityTest {
    @Test
    void fiftyDecisionsAtTheMarginCoverAndAWorseMeanDoesNot() {
        var covered = cell(50, -0.5, 0.005, 0.0);
        assertTrue(covered.lowerBound() >= -0.02);
        assertEquals(Optional.empty(), CoverageRule.cause(false, true, true, true, covered,
                List.of("press"), 50, 0.02));
        var worse = cell(50, -2.5, 0.125, 1.0);
        assertTrue(worse.lowerBound() < -0.02);
        assertEquals(Optional.of(OodCause.OOD_CELL_INFERIOR), CoverageRule.cause(false, true, true, true,
                worse, List.of("press"), 50, 0.02));
        var thin = cell(49, 49.0, 49.0, 1.0);
        assertEquals(Optional.of(OodCause.OOD_CELL_THIN), CoverageRule.cause(false, true, true, true,
                thin, List.of("press"), 50, 0.02));
        assertEquals(NormalBound.Z95, 1.6448536269514722);
    }

    @Test
    void theIndexMarksAModelChangeBeforeAgeAndIgnoresAgreement() {
        var row = cell(80, 8.0, 0.8, 0.1);
        var index = CoverageIndex.of(List.of(row), 10L, "finger", "outcome", true);
        assertEquals(Optional.empty(), index.covered("fine", List.of("press"), 10L, 60, "finger", "outcome", 50,
                0.02));
        var stale = index.markModelStale();
        assertEquals(Optional.of(OodCause.OOD_COVERAGE_STALE), stale.covered("fine", List.of("press"), 10L, 60,
                "finger", "outcome", 50, 0.02));
        assertEquals(Optional.of(OodCause.OOD_PROFILE_CHANGED), index.covered("fine", List.of("press"), 10L, 60,
                "other", "outcome", 50, 0.02));
    }

    private static CoverageCell cell(int n, double sum, double sumSq, double agreement) {
        return new CoverageCell("fine", n, sum, sumSq, List.of("press"), false, agreement);
    }
}
