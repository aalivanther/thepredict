package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactTimingTest {
    @Test
    void theWaitIsHittableAboveWindUp() {
        assertEquals(0, ImpactTiming.ticksUntilHittable(10, 20));
        assertEquals(1, ImpactTiming.ticksUntilHittable(11, 21));
        for (var wind : new int[] {0, 5, 10, 15}) {
            for (var hittable = 0; hittable <= 12; hittable++) {
                assertEquals(hittable > wind, ImpactTiming.mustWait(hittable, wind));
            }
        }
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(3), OptionalDouble.empty(), 4, CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        var facts = new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.of(9),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false, false, true);
        assertEquals(9, ImpactTiming.effectiveWindUpTicks(purpose, facts));
        assertEquals(9, ImpactTiming.effectiveWindUpTicks(facts));
        assertFalse(ImpactTiming.mustWait(9, 9));
        assertTrue(ImpactTiming.mustWait(10, 9));
    }
}
