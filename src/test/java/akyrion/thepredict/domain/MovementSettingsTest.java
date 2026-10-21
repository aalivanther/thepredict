package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementSettingsTest {
    private static IllegalArgumentException invalid(double melee, double inner, double outer, double ranged,
                                                    double retreat, double retreatSpeed, double approachSpeed,
                                                    OptionalDouble arena) {
        return assertThrows(IllegalArgumentException.class, () -> new MovementSettings(melee, inner, outer, ranged,
                retreat, retreatSpeed, approachSpeed, arena));
    }

    @Test
    void documentedDefaultsAreValid() {
        var settings = DomainFixtures.movement();
        assertEquals(2.5, settings.meleeReachBlocks());
        assertEquals(16, settings.rangedReachBlocks());
    }

    @Test
    void meleeReachMustBePositiveAndBelowTheBand() {
        assertTrue(invalid(0, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()).getMessage().contains("meleeReachBlocks"));
        assertTrue(invalid(5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()).getMessage().contains("meleeReachBlocks"));
    }

    @Test
    void bandInnerMustBeBelowBandOuter() {
        assertTrue(invalid(2.5, 9, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()).getMessage().contains("bandInnerBlocks"));
    }

    @Test
    void bandOuterMayEqualButNotExceedRangedReach() {
        assertEquals(9, new MovementSettings(2.5, 5, 9, 9, 10, 1.2, 1.15, OptionalDouble.empty()).rangedReachBlocks());
        assertTrue(invalid(2.5, 5, 9, 8.9, 10, 1.2, 1.15, OptionalDouble.empty()).getMessage().contains("rangedReachBlocks"));
    }

    @Test
    void speedsAndRetreatDistanceMustBeFiniteAndPositive() {
        assertTrue(invalid(2.5, 5, 9, 16, 10, 0, 1.15, OptionalDouble.empty()).getMessage().contains("retreatSpeed"));
        assertTrue(invalid(2.5, 5, 9, 16, 10, 1.2, Double.POSITIVE_INFINITY, OptionalDouble.empty())
                .getMessage().contains("approachSpeed"));
        assertTrue(invalid(2.5, 5, 9, 16, Double.NaN, 1.2, 1.15, OptionalDouble.empty())
                .getMessage().contains("retreatDistanceBlocks"));
    }

    @Test
    void arenaRadiusMustExceedBandOuter() {
        assertTrue(invalid(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.of(9)).getMessage().contains("arenaRadiusBlocks"));
        assertEquals(OptionalDouble.of(9.5),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.of(9.5)).arenaRadiusBlocks());
    }
}
