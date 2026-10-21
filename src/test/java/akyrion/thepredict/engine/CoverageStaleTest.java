package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.distill.CellOrders;
import akyrion.thepredict.domain.distill.CoverageCell;
import akyrion.thepredict.domain.distill.CoverageIndex;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.domain.distill.OodCause;
import akyrion.thepredict.domain.distill.PreferenceTree;
import akyrion.thepredict.domain.distill.RegimeKey;
import akyrion.thepredict.domain.distill.TacticOrder;
import akyrion.thepredict.engine.distill.DistillRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.MODEL;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A discarded model change marks coverage stale. Constructing the expectation alone does not. */
class CoverageStaleTest {
    @Test
    void aDifferentVersionedModelMarksThePublishedIndex() {
        var runtime = published();
        assertTrue(runtime.view("fine", List.of("press"), 0L, 0L, 50, 0.02, 60).covered());
        var fixture = new ExecutionFixture();
        fixture.steps.onCoverageStale(runtime::markModelStale);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        var answers = nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9));
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, answers, "jev-1.14.0"));
        assertEquals(1, fixture.count(DiscardReason.MODEL_CHANGED));
        assertEquals(OodCause.OOD_COVERAGE_STALE, runtime.view("fine", List.of("press"), 0L, 0L, 50, 0.02, 60).ood()
                .orElseThrow());
        assertEquals(MODEL, "jev-1.13.0");
    }

    private static DistillRuntime published() {
        var cell = new CoverageCell("fine", 50, -0.5, 0.005, List.of("press"), false, 0.0);
        var order = new TacticOrder(List.of("press"), List.of(1000), DistillSource.TABLE);
        var policy = new DistilledPolicy("guardian", RegimeKey.ALL, new CellOrders(Map.of("fine", order)),
                PreferenceTree.leaf(order));
        var runtime = new DistillRuntime();
        runtime.publish(CoverageIndex.of(List.of(cell), 0L, "finger", "outcome", true), policy);
        return runtime;
    }
}
