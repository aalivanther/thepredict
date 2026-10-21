package akyrion.thepredict.domain;

import akyrion.thepredict.domain.OptionContext.FitReason;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.choice;
import static akyrion.thepredict.domain.DomainFixtures.declared;
import static akyrion.thepredict.domain.DomainFixtures.profile;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static akyrion.thepredict.domain.DomainFixtures.tacticOf;
import static akyrion.thepredict.domain.DomainFixtures.timing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionContextTest {
    private static SkillPurpose purpose(SkillRole primary, Delivery delivery, OptionalDouble reach, DamageTier damage,
                                        Set<UseCondition> bestWhen, Set<SkillRisk> risks, boolean ignoresShield) {
        var radius = delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET
                ? OptionalDouble.of(3) : OptionalDouble.empty();
        return new SkillPurpose(primary, Set.of(primary), delivery, reach, radius, 5, CooldownClass.SHORT, damage,
                bestWhen, risks, ignoresShield, PurposeOrigin.DECLARED);
    }

    private static TacticalProfile profileWith(Movement movement, SkillPurpose purpose) {
        return profile(List.of(tacticOf("option", movement, "skill", 50), tacticOf("hold", Movement.HOLD, null, 10)),
                List.of(declared("skill", purpose)), List.of(), timing());
    }

    private static MobFacts mob(HealthBand health, DistanceBand distance, boolean reachable, boolean visible,
                                Set<ChoiceId> outOfReach) {
        return new MobFacts(MOB, health, Optional.of(new TargetRelation(new ParticipantAlias(1), distance, Trend.STEADY,
                reachable, visible, false, true, false)), Set.of(), Set.of(), Set.of(), outOfReach);
    }

    private static OptionContext.OptionFit fit(Movement movement, SkillPurpose purpose, MobFacts mob,
                                               UseCondition... now) {
        var profile = profileWith(movement, purpose);
        return OptionContext.of(profile.tactic(tactic("option")).orElseThrow(), profile, mob,
                now.length == 0 ? Set.of() : EnumSet.of(now[0], now));
    }

    private static MobFacts healthyInRange() {
        return mob(HealthBand.HEALTHY, DistanceBand.RANGED, true, true, Set.of());
    }

    @Test
    void eachBestWhenThatHoldsFitsInTheOrderOfTheConditionsAndTheListIsCutAtThree() {
        var purpose = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), DamageTier.MEDIUM,
                EnumSet.of(UseCondition.TARGET_IN_MELEE, UseCondition.TARGET_LOW_HEALTH, UseCondition.ENEMIES_GROUPED,
                        UseCondition.TARGET_SHIELDING), Set.of(), true);
        var result = fit(Movement.FLANK, purpose, mob(HealthBand.HEALTHY, DistanceBand.MELEE, true, true, Set.of()),
                UseCondition.TARGET_IN_MELEE, UseCondition.TARGET_LOW_HEALTH, UseCondition.ENEMIES_GROUPED,
                UseCondition.TARGET_SHIELDING);
        assertEquals(List.of(FitReason.BEST_WHEN_TARGET_LOW_HEALTH, FitReason.BEST_WHEN_TARGET_SHIELDING,
                FitReason.BEST_WHEN_ENEMIES_GROUPED), result.fits());
    }

    @Test
    void aFlankBeatsAShieldAndASkillThroughShieldsGoesThroughIt() {
        var plain = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), DamageTier.MEDIUM, Set.of(),
                Set.of(), false);
        assertEquals(List.of(FitReason.FLANK_BEATS_SHIELD), fit(Movement.FLANK, plain, healthyInRange(),
                UseCondition.TARGET_SHIELDING).fits());
        var piercing = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), DamageTier.MEDIUM, Set.of(),
                Set.of(), true);
        assertEquals(List.of(FitReason.GOES_THROUGH_SHIELD), fit(Movement.APPROACH, piercing, healthyInRange(),
                UseCondition.TARGET_SHIELDING).fits());
        assertEquals(List.of(), fit(Movement.APPROACH, piercing, healthyInRange()).fits());
    }

    @Test
    void aShieldBlocksAnAimedDamagingSkillUnlessItFlanksOrGoesThroughAndNeverAnAreaAroundTheCaster() {
        var plain = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), DamageTier.MEDIUM, Set.of(),
                Set.of(), false);
        assertEquals(List.of(FitReason.SHIELD_BLOCKS_IT), fit(Movement.APPROACH, plain, healthyInRange(),
                UseCondition.TARGET_SHIELDING).poorFits());
        assertEquals(List.of(), fit(Movement.FLANK, plain, healthyInRange(), UseCondition.TARGET_SHIELDING).poorFits());
        var area = purpose(SkillRole.DAMAGE, Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), DamageTier.MEDIUM,
                Set.of(), Set.of(), false);
        assertEquals(List.of(), fit(Movement.APPROACH, area, healthyInRange(), UseCondition.TARGET_SHIELDING)
                .poorFits());
        var heal = purpose(SkillRole.SUSTAIN, Delivery.SELF, OptionalDouble.empty(), DamageTier.NONE, Set.of(),
                Set.of(), false);
        assertTrue(fit(Movement.RETREAT, heal, mob(HealthBand.WOUNDED, DistanceBand.MELEE, true, true, Set.of()),
                UseCondition.TARGET_SHIELDING).poorFits().isEmpty());
    }

    @Test
    void outOfReachCountsOnlyWhenTheMovementDoesNotCloseTheDistance() {
        var melee = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), DamageTier.MEDIUM, Set.of(),
                Set.of(), false);
        var out = mob(HealthBand.HEALTHY, DistanceBand.RANGED, true, true, Set.of(choice("skill")));
        assertEquals(List.of(FitReason.OUT_OF_REACH), fit(Movement.HOLD, melee, out).poorFits());
        assertEquals(List.of(), fit(Movement.APPROACH, melee, out).poorFits());
    }

    @Test
    void lineOfSightExposureFootReachHealthAndDodgeFollowTheirFacts() {
        var sight = purpose(SkillRole.DAMAGE, Delivery.HITSCAN, OptionalDouble.of(16), DamageTier.LOW, Set.of(),
                EnumSet.of(SkillRisk.NEEDS_LINE_OF_SIGHT, SkillRisk.ROOTS_CASTER, SkillRisk.SLOW_PROJECTILE), false);
        var hidden = mob(HealthBand.CRITICAL, DistanceBand.RANGED, true, false, Set.of());
        assertEquals(List.of(FitReason.NO_LINE_OF_SIGHT, FitReason.EXPOSED_WHILE_CRITICAL, FitReason.TARGET_CAN_DODGE),
                fit(Movement.KEEP_DISTANCE, sight, hidden, UseCondition.SELF_LOW_HEALTH, UseCondition.TARGET_FLEEING)
                        .poorFits());
        var melee = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), DamageTier.MEDIUM, Set.of(),
                Set.of(), false);
        var pillar = mob(HealthBand.HEALTHY, DistanceBand.RANGED, false, true, Set.of());
        assertEquals(List.of(FitReason.CANNOT_REACH_ON_FOOT), fit(Movement.HOLD, melee, pillar).poorFits());
        var heal = purpose(SkillRole.SUSTAIN, Delivery.SELF, OptionalDouble.empty(), DamageTier.NONE, Set.of(),
                Set.of(), false);
        assertEquals(List.of(FitReason.CASTER_ALREADY_HEALTHY), fit(Movement.RETREAT, heal, healthyInRange())
                .poorFits());
        var profile = profileWith(Movement.APPROACH, heal);
        var noChoice = profile.tactic(tactic("hold")).orElseThrow();
        assertEquals(OptionContext.OptionFit.NONE, OptionContext.of(noChoice, profile, pillar, Set.of()));
    }

    @Test
    void aContingencyIsJudgedUnderItsHypothesis() {
        var melee = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), DamageTier.MEDIUM,
                EnumSet.of(UseCondition.TARGET_IN_MELEE), EnumSet.of(SkillRisk.ROOTS_CASTER), false);
        var profile = profileWith(Movement.HOLD, melee);
        var option = profile.tactic(tactic("option")).orElseThrow();
        var far = mob(HealthBand.HEALTHY, DistanceBand.RANGED, true, true, Set.of(choice("skill")));
        var now = EnumSet.of(UseCondition.TARGET_AT_RANGE, UseCondition.TARGET_FLEEING);
        assertEquals(List.of(FitReason.OUT_OF_REACH), OptionContext.of(option, profile, far, now).poorFits());
        var inMelee = OptionContext.forSituation(option, profile, far, now, Situation.IF_TARGET_IN_MELEE);
        assertEquals(List.of(FitReason.BEST_WHEN_TARGET_IN_MELEE), inMelee.fits());
        assertEquals(List.of(), inMelee.poorFits());
        assertEquals(EnumSet.of(UseCondition.TARGET_IN_MELEE),
                OptionContext.hypothetical(now, Situation.IF_TARGET_IN_MELEE));
        var critical = OptionContext.forSituation(option, profile, far, now, Situation.IF_OWN_HEALTH_CRITICAL);
        assertTrue(critical.poorFits().contains(FitReason.EXPOSED_WHILE_CRITICAL));
        assertEquals(now, OptionContext.hypothetical(now, Situation.NOW));
    }

    @Test
    void everyConditionHasItsOwnBestWhenReasonAndTheListsAreBounded() {
        for (var condition : UseCondition.values()) {
            assertEquals("BEST_WHEN_" + condition.name(), FitReason.bestWhen(condition).name());
        }
        var four = List.of(FitReason.OUT_OF_REACH, FitReason.NO_LINE_OF_SIGHT, FitReason.SHIELD_BLOCKS_IT,
                FitReason.TARGET_CAN_DODGE);
        assertThrows(IllegalArgumentException.class, () -> new OptionContext.OptionFit(List.of(), four));
    }
}
