package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPurposeTest {
    private static final OptionalDouble NONE = OptionalDouble.empty();

    @Test
    void reachIsAcceptedUpTo64BlocksAndRejectedAbove() {
        assertEquals(64.0, melee(64.0).reachBlocks().getAsDouble());
        assertEquals(0.0001, melee(0.0001).reachBlocks().getAsDouble());
        var above = assertThrows(IllegalArgumentException.class, () -> melee(64.0001));
        assertTrue(above.getMessage().startsWith("reachBlocks must be finite, greater than 0 and at most 64.0"));
        assertThrows(IllegalArgumentException.class, () -> melee(0.0));
        assertThrows(IllegalArgumentException.class, () -> melee(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> melee(Double.POSITIVE_INFINITY));
    }

    @Test
    void reachIsRequiredForMeleeAndConeAndForbiddenForSelfAndAreaAroundSelf() {
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.MELEE, NONE, NONE));
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.CONE, NONE, NONE));
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.SELF, OptionalDouble.of(2), NONE));
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.AREA_AROUND_SELF, OptionalDouble.of(2),
                OptionalDouble.of(3)));
        of(Delivery.PROJECTILE, NONE, NONE);
        of(Delivery.PROJECTILE, OptionalDouble.of(20), NONE);
        of(Delivery.HITSCAN, OptionalDouble.of(20), NONE);
        of(Delivery.AREA_AT_TARGET, OptionalDouble.of(20), OptionalDouble.of(3));
        of(Delivery.AREA_AT_TARGET, NONE, OptionalDouble.of(3));
    }

    @Test
    void radiusIsPresentExactlyForTheAreasAndUpTo16Blocks() {
        assertEquals(16.0, of(Delivery.AREA_AROUND_SELF, NONE, OptionalDouble.of(16)).radiusBlocks().getAsDouble());
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.AREA_AROUND_SELF, NONE, OptionalDouble.of(16.0001)));
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.AREA_AROUND_SELF, NONE, NONE));
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.AREA_AT_TARGET, NONE, NONE));
        var cone = assertThrows(IllegalArgumentException.class, () -> of(Delivery.CONE, OptionalDouble.of(4),
                OptionalDouble.of(2)));
        assertEquals("radiusBlocks must be absent for delivery CONE", cone.getMessage());
        assertThrows(IllegalArgumentException.class, () -> of(Delivery.SELF, NONE, OptionalDouble.of(2)));
    }

    @Test
    void windUpIsBetweenZeroAndOneHundredTicks() {
        purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 0, Set.of(), Set.of(), false);
        purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 100, Set.of(), Set.of(), false);
        assertThrows(IllegalArgumentException.class, () -> purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 101,
                Set.of(), Set.of(), false));
        assertThrows(IllegalArgumentException.class, () -> purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, -1,
                Set.of(), Set.of(), false));
    }

    @Test
    void rolesHoldThePrimaryAndAtMostThreeEntries() {
        purpose(Set.of(SkillRole.DAMAGE, SkillRole.CONTROL, SkillRole.OPENER), DamageTier.LOW, 0, Set.of(), Set.of(),
                false);
        assertThrows(IllegalArgumentException.class, () -> purpose(Set.of(SkillRole.DAMAGE, SkillRole.CONTROL,
                SkillRole.OPENER, SkillRole.ZONE), DamageTier.LOW, 0, Set.of(), Set.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.CONTROL),
                Delivery.SELF, NONE, NONE, 0, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED));
    }

    @Test
    void damageAndFinisherRolesNeedDamage() {
        var damage = assertThrows(IllegalArgumentException.class, () -> purpose(Set.of(SkillRole.DAMAGE),
                DamageTier.NONE, 0, Set.of(), Set.of(), false));
        assertEquals("damage must not be NONE when roles has DAMAGE or FINISHER", damage.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new SkillPurpose(SkillRole.FINISHER,
                Set.of(SkillRole.FINISHER), Delivery.SELF, NONE, NONE, 0, CooldownClass.UNKNOWN, DamageTier.NONE,
                Set.of(), Set.of(), false, PurposeOrigin.DECLARED));
        new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN), Delivery.SELF, NONE, NONE, 0,
                CooldownClass.UNKNOWN, DamageTier.NONE, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
    }

    @Test
    void fourConditionsAndFourRisksAreAcceptedButNotFive() {
        var four = Set.of(UseCondition.TARGET_LOW_HEALTH, UseCondition.TARGET_SHIELDING, UseCondition.TARGET_FLEEING,
                UseCondition.SELF_LOW_HEALTH);
        var five = EnumSet.copyOf(four);
        five.add(UseCondition.ENEMIES_GROUPED);
        purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 0, four, Set.of(), false);
        assertThrows(IllegalArgumentException.class, () -> purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 0, five,
                Set.of(), false));
        var fourRisks = Set.of(SkillRisk.ROOTS_CASTER, SkillRisk.SELF_DAMAGE, SkillRisk.HITS_ALLIES,
                SkillRisk.SLOW_PROJECTILE);
        var fiveRisks = EnumSet.copyOf(fourRisks);
        fiveRisks.add(SkillRisk.NEEDS_LINE_OF_SIGHT);
        purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 0, Set.of(), fourRisks, false);
        assertThrows(IllegalArgumentException.class, () -> purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 0,
                Set.of(), fiveRisks, false));
    }

    @Test
    void ignoringShieldsExcludesTheRiskOfBeingBlockedByOne() {
        var exception = assertThrows(IllegalArgumentException.class, () -> purpose(Set.of(SkillRole.DAMAGE),
                DamageTier.LOW, 0, Set.of(), Set.of(SkillRisk.BLOCKED_BY_SHIELD), true));
        assertEquals("ignoresShield cannot be true while risks has BLOCKED_BY_SHIELD", exception.getMessage());
        purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 0, Set.of(), Set.of(SkillRisk.BLOCKED_BY_SHIELD), false);
        purpose(Set.of(SkillRole.DAMAGE), DamageTier.LOW, 0, Set.of(), Set.of(), true);
    }

    @Test
    void damagingAndAimedAtTargetForTheSevenDeliveries() {
        var aimed = new HashSet<Delivery>();
        for (var delivery : Delivery.values()) {
            var purpose = of(delivery, delivery == Delivery.MELEE || delivery == Delivery.CONE ? OptionalDouble.of(3)
                    : NONE, delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET
                    ? OptionalDouble.of(3) : NONE);
            assertTrue(purpose.damaging(), delivery.name());
            if (purpose.aimedAtTarget()) {
                aimed.add(delivery);
            }
        }
        assertEquals(EnumSet.of(Delivery.MELEE, Delivery.PROJECTILE, Delivery.HITSCAN, Delivery.AREA_AT_TARGET,
                Delivery.CONE), aimed);
        var buff = new SkillPurpose(SkillRole.BUFF, Set.of(SkillRole.BUFF), Delivery.SELF, NONE, NONE, 0,
                CooldownClass.UNKNOWN, DamageTier.NONE, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        assertFalse(buff.damaging());
        var unrated = new SkillPurpose(SkillRole.DEBUFF, Set.of(SkillRole.DEBUFF), Delivery.HITSCAN, NONE, NONE, 0,
                CooldownClass.UNKNOWN, DamageTier.UNRATED, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        assertTrue(unrated.damaging());
        var finisher = new SkillPurpose(SkillRole.FINISHER, Set.of(SkillRole.FINISHER), Delivery.HITSCAN, NONE, NONE, 0,
                CooldownClass.UNKNOWN, DamageTier.HIGH, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        assertTrue(finisher.damaging());
    }

    @Test
    void onlyDeclaredAndSkillPurposesAreAuthored() {
        assertTrue(melee(2.5).authored());
        assertTrue(withOrigin(PurposeOrigin.SKILL).authored());
        assertFalse(withOrigin(PurposeOrigin.INFERRED).authored());
    }

    @Test
    void theEffectiveReachIsTheRadiusAroundSelfAndTheReachOtherwise() {
        assertEquals(3.0, of(Delivery.AREA_AROUND_SELF, NONE, OptionalDouble.of(3)).effectiveReachBlocks().getAsDouble());
        assertEquals(12.0, of(Delivery.AREA_AT_TARGET, OptionalDouble.of(12), OptionalDouble.of(3))
                .effectiveReachBlocks().getAsDouble());
        assertTrue(of(Delivery.PROJECTILE, NONE, NONE).effectiveReachBlocks().isEmpty());
    }

    @Test
    void setsAreDefensiveCopiesAndEqualityIsByValue() {
        var roles = EnumSet.of(SkillRole.DAMAGE);
        var conditions = EnumSet.of(UseCondition.TARGET_IN_MELEE);
        var purpose = new SkillPurpose(SkillRole.DAMAGE, roles, Delivery.MELEE, OptionalDouble.of(2.5), NONE, 5,
                CooldownClass.SHORT, DamageTier.MEDIUM, conditions, Set.of(), false, PurposeOrigin.DECLARED);
        roles.add(SkillRole.CONTROL);
        conditions.add(UseCondition.TARGET_FLEEING);
        assertEquals(Set.of(SkillRole.DAMAGE), purpose.roles());
        assertEquals(Set.of(UseCondition.TARGET_IN_MELEE), purpose.bestWhen());
        assertThrows(UnsupportedOperationException.class, () -> purpose.risks().add(SkillRisk.SELF_DAMAGE));
        assertEquals(purpose, new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), NONE, 5, CooldownClass.SHORT, DamageTier.MEDIUM,
                Set.of(UseCondition.TARGET_IN_MELEE), Set.of(), false, PurposeOrigin.DECLARED));
    }

    @Test
    void theCooldownAndTheShieldCopiesKeepEverythingElse() {
        var purpose = melee(2.5);
        assertSame(purpose, purpose.withCooldown(CooldownClass.UNKNOWN));
        var resolved = purpose.withCooldown(CooldownClass.LONG);
        assertEquals(CooldownClass.LONG, resolved.cooldown());
        assertEquals(purpose, resolved.withCooldown(CooldownClass.UNKNOWN));
        var shielded = purpose.ignoringShields();
        assertTrue(shielded.ignoresShield());
        assertSame(shielded, shielded.ignoringShields());
    }

    @Test
    void thePrimaryRoleComesFirstForPresentation() {
        var purpose = new SkillPurpose(SkillRole.OPENER, Set.of(SkillRole.OPENER, SkillRole.DAMAGE, SkillRole.CONTROL),
                Delivery.HITSCAN, NONE, NONE, 0, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        assertEquals(List.of(SkillRole.OPENER, SkillRole.DAMAGE, SkillRole.CONTROL), List.copyOf(purpose.orderedRoles()));
    }

    private static SkillPurpose melee(double reach) {
        return of(Delivery.MELEE, OptionalDouble.of(reach), NONE);
    }

    private static SkillPurpose of(Delivery delivery, OptionalDouble reach, OptionalDouble radius) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery, reach, radius, 0,
                CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
    }

    private static SkillPurpose withOrigin(PurposeOrigin origin) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.HITSCAN, NONE, NONE, 0,
                CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), Set.of(), false, origin);
    }

    private static SkillPurpose purpose(Set<SkillRole> roles, DamageTier damage, int windUp, Set<UseCondition> bestWhen,
                                        Set<SkillRisk> risks, boolean ignoresShield) {
        return new SkillPurpose(SkillRole.DAMAGE, roles, Delivery.HITSCAN, NONE, NONE, windUp, CooldownClass.UNKNOWN,
                damage, bestWhen, risks, ignoresShield, PurposeOrigin.DECLARED);
    }

    @Test
    void twoPurposesAreSemanticallyEqualWhenOnlyTheirOriginDiffers() {
        var declared = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 5, CooldownClass.SHORT, DamageTier.MEDIUM,
                Set.of(UseCondition.TARGET_IN_MELEE), Set.of(), false, PurposeOrigin.DECLARED);
        var inferred = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 5, CooldownClass.SHORT, DamageTier.MEDIUM,
                Set.of(UseCondition.TARGET_IN_MELEE), Set.of(), false, PurposeOrigin.INFERRED);
        assertTrue(declared.semanticallyEqual(inferred));
        assertFalse(declared.semanticallyEqual(declared.withCooldown(CooldownClass.LONG)));
        assertFalse(declared.semanticallyEqual(declared.ignoringShields()));
    }
}
