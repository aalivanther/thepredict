package akyrion.thepredict.domain;

import akyrion.thepredict.domain.PurposeRules.PurposeFit;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.meleePurpose;
import static akyrion.thepredict.domain.DomainFixtures.purpose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurposeRulesTest {
    private static final MovementSettings DEFAULTS = MovementSettings.DEFAULTS;
    private static final MovementSettings WIDE = new MovementSettings(3, 6, 12, 20, 10, 1.2, 1.15, OptionalDouble.empty());

    @Test
    void approachAndFlankFitDownToAQuarterBlockUnderMeleeReach() {
        for (var movement : new Movement[] {Movement.APPROACH, Movement.FLANK}) {
            assertEquals(PurposeFit.FITS, fit(movement, 2.25, DEFAULTS));
            assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(movement, 2.2499, DEFAULTS));
            assertEquals(PurposeFit.FITS, fit(movement, 2.75, WIDE));
            assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(movement, 2.7499, WIDE));
        }
    }

    @Test
    void keepDistanceNeverCastsBelowTheInnerEdgeAndFitsFromTheOuterEdge() {
        assertEquals(PurposeFit.FITS, fit(Movement.KEEP_DISTANCE, 9, DEFAULTS));
        assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(Movement.KEEP_DISTANCE, 8.9999, DEFAULTS));
        assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(Movement.KEEP_DISTANCE, 5, DEFAULTS));
        assertEquals(PurposeFit.NEVER_CASTS, fit(Movement.KEEP_DISTANCE, 4.9999, DEFAULTS));
        assertEquals(PurposeFit.NEVER_CASTS, fit(Movement.KEEP_DISTANCE, 2.5, DEFAULTS));
        assertEquals(PurposeFit.FITS, fit(Movement.KEEP_DISTANCE, 12, WIDE));
        assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(Movement.KEEP_DISTANCE, 6, WIDE));
        assertEquals(PurposeFit.NEVER_CASTS, fit(Movement.KEEP_DISTANCE, 5.9999, WIDE));
    }

    @Test
    void retreatAndHoldFitOnlyFromRangedReach() {
        for (var movement : new Movement[] {Movement.RETREAT, Movement.HOLD}) {
            assertEquals(PurposeFit.FITS, fit(movement, 16, DEFAULTS));
            assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(movement, 15.9999, DEFAULTS));
            assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(movement, 1, DEFAULTS));
            assertEquals(PurposeFit.FITS, fit(movement, 20, WIDE));
            assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, fit(movement, 19.9999, WIDE));
        }
    }

    @Test
    void noneAlwaysFitsBecauseTheNativeMovementIsUnknown() {
        assertEquals(PurposeFit.FITS, fit(Movement.NONE, 0.5, DEFAULTS));
    }

    @Test
    void aSkillWithoutAReachLimitAlwaysFits() {
        var self = new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN), Delivery.SELF,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE,
                Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        var projectile = purpose(Delivery.PROJECTILE, OptionalDouble.empty(), OptionalDouble.empty());
        for (var movement : Movement.values()) {
            assertEquals(PurposeFit.FITS, PurposeRules.fit(movement, self, DEFAULTS), movement.name());
            assertEquals(PurposeFit.FITS, PurposeRules.fit(movement, projectile, DEFAULTS), movement.name());
        }
    }

    @Test
    void anAreaAroundTheCasterUsesItsRadiusAsReach() {
        var slam = purpose(Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), OptionalDouble.of(3));
        assertEquals(PurposeFit.FITS, PurposeRules.fit(Movement.APPROACH, slam, DEFAULTS));
        assertEquals(PurposeFit.CASTS_ONLY_IF_TARGET_COMES, PurposeRules.fit(Movement.HOLD, slam, DEFAULTS));
        assertEquals(PurposeFit.NEVER_CASTS, PurposeRules.fit(Movement.KEEP_DISTANCE, slam, DEFAULTS));
    }

    @Test
    void approachFlankAndAFarReachingKeepDistanceCloseTheDistance() {
        assertTrue(PurposeRules.closesDistance(Movement.APPROACH, meleePurpose(2.5), DEFAULTS));
        assertTrue(PurposeRules.closesDistance(Movement.FLANK, meleePurpose(2.5), DEFAULTS));
        assertTrue(PurposeRules.closesDistance(Movement.KEEP_DISTANCE, meleePurpose(9), DEFAULTS));
        assertFalse(PurposeRules.closesDistance(Movement.KEEP_DISTANCE, meleePurpose(8.9999), DEFAULTS));
        assertFalse(PurposeRules.closesDistance(Movement.KEEP_DISTANCE, purpose(Delivery.PROJECTILE,
                OptionalDouble.empty(), OptionalDouble.empty()), DEFAULTS));
        for (var movement : new Movement[] {Movement.RETREAT, Movement.HOLD, Movement.NONE}) {
            assertFalse(PurposeRules.closesDistance(movement, meleePurpose(20), DEFAULTS), movement.name());
        }
    }

    @Test
    void vanillaMeleeIsADamagingMeleeHitAndAVeryLongReachIsCapped() {
        var punch = PurposeRules.vanillaMelee(DEFAULTS);
        assertEquals(Delivery.MELEE, punch.delivery());
        assertEquals(SkillRole.DAMAGE, punch.primary());
        assertEquals(2.5, punch.reachBlocks().getAsDouble());
        assertEquals(DamageTier.UNRATED, punch.damage());
        assertEquals(PurposeOrigin.INFERRED, punch.origin());
        assertEquals(Set.of(UseCondition.TARGET_IN_MELEE), punch.bestWhen());
        var wide = new MovementSettings(70, 71, 72, 80, 10, 1.2, 1.15, OptionalDouble.empty());
        assertEquals(SkillPurpose.MAX_REACH_BLOCKS, PurposeRules.vanillaMelee(wide).reachBlocks().getAsDouble());
    }

    @Test
    void theDefaultAreaRadiusStaysThreeBlocks() {
        assertEquals(3.0, PurposeRules.DEFAULT_AREA_RADIUS_BLOCKS);
    }

    @Test
    void anUntypedChoiceUsesTheConservativeDefaults() {
        var defaults = PurposeRules.UNTYPED_DEFAULTS;
        assertEquals(Set.of(), defaults.roles());
        assertEquals(DamageTier.UNRATED, defaults.damage());
        assertTrue(defaults.damaging());
        assertFalse(defaults.reachChecked());
    }

    private static PurposeFit fit(Movement movement, double reachBlocks, MovementSettings settings) {
        return PurposeRules.fit(movement, meleePurpose(reachBlocks), settings);
    }
}
