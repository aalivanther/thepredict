package akyrion.thepredict.learn;

import akyrion.thepredict.domain.learn.RewardSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skirmish seed is stable. The 200-seed matrix prints its decision and does not move the reward. */
class RewardAlignmentBaselineTest {
    @Test
    void oneSkirmishSeedRepeatsItsDeathTick() {
        assertEquals(RewardAlignmentBaseline.deathTick(28L), RewardAlignmentBaseline.deathTick(28L));
    }

    @Test
    void theMatrixPrintsThePairedDecisionWithoutMovingTheReward() {
        var line = RewardAlignmentBaseline.report();
        System.out.println(line);
        assertTrue(line.contains("n=200"));
        assertTrue(line.contains("decision=GAIN") || line.contains("decision=NO CUMPLE"));
        assertEquals(0.5, RewardSpec.ETA, 0.0);
        assertEquals(0.8, RewardSpec.BETA, 0.0);
        assertTrue(line.contains("eta=0.5"));
        assertTrue(line.contains("beta=0.8"));
    }
}
