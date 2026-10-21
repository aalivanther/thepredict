package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pick is always one of the live candidates. */
class DistilledPolicyTest {
    @Test
    void aHundredThousandCapturesStayInsideTheCandidates() {
        var order = new TacticOrder(List.of("t0", "t1", "t2"), List.of(3, 2, 1), DistillSource.TABLE);
        var policy = new DistilledPolicy("guardian", RegimeKey.ALL, new CellOrders(Map.of("fine", order)),
                PreferenceTree.leaf(order));
        var candidates = List.of("t2", "t1");
        for (var index = 0; index < 100_000; index++) {
            var choice = policy.choose(candidates, index % 2 == 0 ? "fine" : "other", new int[] {0, 0, 0, 0, 0, 0, 0});
            assertTrue(choice.tactic().isPresent());
            assertTrue(candidates.contains(choice.tactic().orElseThrow()));
        }
        assertEquals("t1", policy.choose(candidates, "fine", new int[] {0}).tactic().orElseThrow());
        assertEquals(OodCause.OOD_TACTIC_UNSEEN, policy.choose(List.of("absent"), "fine", new int[] {0}).ood().orElseThrow());
    }
}
