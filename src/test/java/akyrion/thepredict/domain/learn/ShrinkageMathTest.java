package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShrinkageMathTest {
    @Test
    void posteriorPullsTowardTheParentAndKappaStaysInsideTheBand() {
        assertEquals(4.0, ShrinkageMath.kappa(1.0, 10.0));
        assertEquals(100.0, ShrinkageMath.kappa(10.0, 0.01));
        assertEquals(0.6, ShrinkageMath.shrink(1, 1.0, 4, 0.5), 1.0e-12);
        assertEquals(600, ShrinkageMath.millis(0.6));
        assertEquals(1.0, ShrinkageMath.weekWeight(0, 14));
        var between = ShrinkageMath.between(new double[] {0.0, 1.0}, new double[] {10, 10}, 0.01);
        assertEquals(0.249, between, 1.0e-12);
    }
}
