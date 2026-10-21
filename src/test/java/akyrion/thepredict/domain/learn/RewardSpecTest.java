package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardSpecTest {
    @Test
    void publishedConstantsMatchThePlan() {
        assertEquals("r1", RewardSpec.ID);
        assertEquals(0.5, RewardSpec.ETA);
        assertEquals(0.5, RewardSpec.GROUP);
        assertEquals(0.8, RewardSpec.BETA);
        assertEquals(1.0, RewardSpec.ZETA);
        assertEquals(0.5, CreditMath.effectTerm(1));
        assertEquals(1.0, CreditMath.effectTerm(9));
        assertEquals(-1.0, CreditMath.effectTerm(-9));
        assertEquals(0.32768, CreditMath.deferred(1.0, 5), 1.0e-12);
        assertEquals(-1, CreditMath.immediateSign(-0.1, 0.0));
        assertEquals(0, CreditMath.immediateSign(0.0, 0.0));
        assertEquals(1, CreditMath.immediateSign(0.2, 0.0));
    }
}
