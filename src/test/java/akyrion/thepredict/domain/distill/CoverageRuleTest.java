package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each coverage condition returns its own cause. Agreement is not an input. */
class CoverageRuleTest {
    @Test
    void theFirstFailingConditionWinsAndTheMarginIsTwoSided() {
        var offered = List.of("press");
        var cell = new CoverageCell("fine", 50, 0.0, 0.0, offered, false, 0.0);
        assertEquals(OodCause.OOD_COVERAGE_STALE, cause(true, true, true, true, cell, offered, 50, 0.02));
        assertEquals(OodCause.OOD_PROFILE_CHANGED, cause(false, false, true, true, cell, offered, 50, 0.02));
        assertEquals(OodCause.OOD_MODEL_CHANGED, cause(false, true, false, true, cell, offered, 50, 0.02));
        assertEquals(OodCause.OOD_REGIME_INACTIVE, cause(false, true, true, false, cell, offered, 50, 0.02));
        assertEquals(OodCause.OOD_CELL_UNSEEN, CoverageRule.unseen(false, true, true, true).orElseThrow());
        assertEquals(OodCause.OOD_COVERAGE_STALE, CoverageRule.unseen(true, true, true, true).orElseThrow());
        assertEquals(OodCause.OOD_REGIME_INACTIVE, CoverageRule.unseen(false, true, true, false).orElseThrow());
        var drifted = new CoverageCell("fine", 50, 0.0, 0.0, offered, true, 1.0);
        assertEquals(OodCause.OOD_DRIFT, cause(false, true, true, true, drifted, offered, 50, 0.02));
        var thin = new CoverageCell("fine", 49, 0.0, 0.0, offered, false, 1.0);
        assertEquals(OodCause.OOD_CELL_THIN, cause(false, true, true, true, thin, offered, 50, 0.02));
        var inferior = new CoverageCell("fine", 50, -2.5, 0.0, offered, false, 1.0);
        assertEquals(OodCause.OOD_CELL_INFERIOR, cause(false, true, true, true, inferior, offered, 50, 0.02));
        assertEquals(OodCause.OOD_TACTIC_UNSEEN, cause(false, true, true, true, cell, List.of("other"), 50, 0.02));
        assertTrue(CoverageRule.cause(false, true, true, true, cell, offered, 50, 0.02).isEmpty());
        var onMargin = new CoverageCell("fine", 50, -1.0, 0.0, offered, false, 0.0);
        assertTrue(CoverageRule.cause(false, true, true, true, onMargin, offered, 50, 0.02).isEmpty());
    }

    @Test
    void weeklyRowsOfOneContextAddUpInsteadOfTheLastOneWinning() {
        var first = new CoverageCell("fine", 30, 0.0, 0.03, List.of("press"), false, 1.0);
        var second = new CoverageCell("fine", 30, 0.0, 0.03, List.of("guard"), false, 0.0);
        var index = CoverageIndex.of(List.of(first, second), 0L, "finger", "outcome", true);
        var merged = index.lookup("fine").orElseThrow();
        assertEquals(60, merged.n());
        assertEquals(0.06, merged.sumDeltaSq(), 1.0e-12);
        assertEquals(List.of("press", "guard"), merged.offered());
        assertEquals(0.5, merged.agreement(), 1.0e-12);
        assertTrue(index.covered("fine", List.of("press", "guard"), 0L, 60, "finger", "outcome", 50, 0.02).isEmpty());
        assertEquals(OodCause.OOD_CELL_THIN, CoverageIndex.of(List.of(second), 0L, "finger", "outcome", true)
                .covered("fine", List.of("guard"), 0L, 60, "finger", "outcome", 50, 0.02).orElseThrow());
    }

    private static OodCause cause(boolean stale, boolean profile, boolean model, boolean regime, CoverageCell cell,
                                  List<String> candidates, int min, double margin) {
        return CoverageRule.cause(stale, profile, model, regime, cell, candidates, min, margin).orElseThrow();
    }
}
