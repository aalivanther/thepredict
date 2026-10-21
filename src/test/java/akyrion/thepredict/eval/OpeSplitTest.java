package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeSplitTest {
    @Test
    void estimationDoesNotReuseTheConstructionSeed() {
        assertTrue(OffPolicyStudy.estimateUsesOnlyOddSeeds());
        var planned = List.of(new OffPolicyEstimator.Planned(2L, 1, 0, 0.5),
                new OffPolicyEstimator.Planned(3L, 1, 0, 0.5));
        var acceptance = List.of(new OffPolicyEstimator.Acceptance(3L, 1, 0));
        var credit = List.of(new OffPolicyEstimator.CreditRow(3L, 1, 0.4));
        var joined = OffPolicyEstimator.join(planned, acceptance, credit, Map.of(0, 0.4), 0.4, 0);
        assertEquals(1, joined.size());
        assertEquals(3L, joined.getFirst().seed());
    }
}
