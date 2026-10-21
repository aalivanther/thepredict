package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvoidedDamageTest {
    @Test
    void anUnchangedDamageRateAvoidsNothing() {
        var rate = 0.10 / 60.0;
        assertEquals(0.0, CreditMath.avoided(rate, rate, 40, true), 0.0);
    }

    @Test
    void halvingTheRateAvoidsHalfTheRateOverTheWindow() {
        var rate = 0.30 / 60.0;
        var expected = 0.5 * rate * 40;
        assertEquals(expected, CreditMath.avoided(rate, rate / 2.0, 40, true), 1.0e-12);
        assertEquals(0.0, CreditMath.avoided(rate, rate / 2.0, 40, false), 0.0);
        assertEquals(1.0, CreditMath.avoided(1.0, 0.0, 40, true), 0.0);
        assertEquals(0.0, CreditMath.avoided(Double.NaN, 0.0, 40, true), 0.0);
    }

    @Test
    void theHealthInPointsTakesNoPartBecauseBothRatesAreFractions() {
        var before = 0.20 / 60.0;
        var during = 0.10 / 40.0;
        var fraction = CreditMath.avoided(before, during, 40, true);
        assertEquals(fraction, CreditMath.avoided(before, during, 40, 100.0, true), 0.0);
        assertEquals(fraction, CreditMath.avoided(before, during, 40, 2000.0, true), 0.0);
        assertTrue(fraction < 1.0);
    }

    @Test
    void defensiveComesFromTheMovementAndTheRolesNotTheName() {
        assertTrue(RewardSpec.defensive(Movement.RETREAT, Set.of(SkillRole.DAMAGE)));
        assertTrue(RewardSpec.defensive(Movement.HOLD, Set.of(SkillRole.DEFENSE)));
        assertTrue(RewardSpec.defensive(Movement.KEEP_DISTANCE, Set.of(SkillRole.SUSTAIN)));
        assertFalse(RewardSpec.defensive(Movement.APPROACH, Set.of(SkillRole.DAMAGE, SkillRole.CONTROL)));
        assertFalse(RewardSpec.defensive(Movement.HOLD, Set.of()));
    }
}
