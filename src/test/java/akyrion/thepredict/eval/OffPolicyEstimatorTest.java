package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffPolicyEstimatorTest {
    @Test
    void theJoinedRowsPublishIpsSnipsAndDoublyRobust() {
        var line = OffPolicyStudy.line();
        System.out.println(line);
        assertTrue(line.startsWith("E3 seed=28 n=2000 epsilon=0.1"));
        assertTrue(line.contains(" ips="));
        assertTrue(line.contains(" snips="));
        assertTrue(line.contains(" dr="));
        assertTrue(Math.abs(OffPolicyStudy.bias()) <= 0.005);
        var explored = OffPolicyStudy.explored();
        assertTrue(explored.support() >= 0.99);
        assertTrue(explored.ess() >= 100.0);
        assertTrue(Math.abs(explored.dm() - 0.8) > 0.5);
        var row = new OffPolicyEstimator.Joined(1L, 1, 0, 0.5, 0, 0.8, 0.0, 0.0);
        var direct = OffPolicyEstimator.estimate(java.util.List.of(row), new double[] {0.5});
        assertEquals(1.6, direct.dr(), 1.0e-9);
        assertEquals(0.0, direct.dm(), 1.0e-9);
        var dark = OffPolicyStudy.withoutExploration();
        assertTrue(dark.support() < 0.99);
        assertEquals("INCONCLUSIVE", dark.decision());
    }
}
