package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeClusterBootstrapTest {
    @Test
    void aClusterIntervalIsWiderThanTheNaiveOne() {
        var rewards = new double[100];
        var encounters = new int[100];
        for (var index = 0; index < rewards.length; index++) {
            encounters[index] = index / 10;
            rewards[index] = encounters[index];
        }
        assertTrue(OffPolicyEstimator.clusterGap(rewards, encounters) > 0.0);
    }
}
