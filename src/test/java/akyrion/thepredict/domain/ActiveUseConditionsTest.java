package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActiveUseConditionsTest {
    private static final ParticipantAlias TARGET = new ParticipantAlias(1);

    @Test
    void withoutATargetOnlyTheCasterHealthCounts() {
        assertEquals(Set.of(UseCondition.SELF_LOW_HEALTH), ActiveUseConditions.of(mob(HealthBand.CRITICAL,
                Optional.empty()), List.of(player(HealthBand.CRITICAL, TargetCue.BLOCKING))));
        assertEquals(Set.of(), ActiveUseConditions.of(mob(HealthBand.WOUNDED, Optional.empty()), List.of()));
    }

    @Test
    void theDistanceBandsGiveMeleeOrRangeAndBeyondGivesNeither() {
        assertEquals(Set.of(UseCondition.TARGET_IN_MELEE), conditions(DistanceBand.MELEE, Trend.STEADY, false));
        assertEquals(Set.of(UseCondition.TARGET_AT_RANGE), conditions(DistanceBand.RANGED, Trend.STEADY, false));
        assertEquals(Set.of(), conditions(DistanceBand.BEYOND, Trend.STEADY, false));
    }

    @Test
    void aTargetBackingAwayFleesOnlyOutsideMelee() {
        assertEquals(Set.of(UseCondition.TARGET_IN_MELEE), conditions(DistanceBand.MELEE, Trend.BACKING_AWAY, false));
        assertEquals(EnumSet.of(UseCondition.TARGET_FLEEING, UseCondition.TARGET_AT_RANGE),
                conditions(DistanceBand.RANGED, Trend.BACKING_AWAY, false));
        assertEquals(Set.of(UseCondition.TARGET_FLEEING), conditions(DistanceBand.BEYOND, Trend.BACKING_AWAY, false));
        assertEquals(Set.of(), conditions(DistanceBand.BEYOND, Trend.CLOSING, false));
    }

    @Test
    void aGroupedTargetStandsCloseToOtherEnemies() {
        assertEquals(EnumSet.of(UseCondition.ENEMIES_GROUPED, UseCondition.TARGET_IN_MELEE),
                conditions(DistanceBand.MELEE, Trend.STEADY, true));
    }

    @Test
    void theTargetHealthAndShieldComeFromItsParticipantFacts() {
        var mob = mob(HealthBand.CRITICAL, Optional.of(relation(DistanceBand.BEYOND, Trend.STEADY, false)));
        assertEquals(EnumSet.of(UseCondition.TARGET_LOW_HEALTH, UseCondition.SELF_LOW_HEALTH),
                ActiveUseConditions.of(mob, player(HealthBand.CRITICAL)));
        assertEquals(EnumSet.of(UseCondition.TARGET_SHIELDING, UseCondition.SELF_LOW_HEALTH),
                ActiveUseConditions.of(mob, player(HealthBand.WOUNDED, TargetCue.BLOCKING)));
        assertEquals(EnumSet.of(UseCondition.TARGET_SHIELDING, UseCondition.SELF_LOW_HEALTH),
                ActiveUseConditions.of(mob, List.of(player(HealthBand.HEALTHY, TargetCue.RAISING_SHIELD))));
        assertEquals(Set.of(UseCondition.SELF_LOW_HEALTH), ActiveUseConditions.of(mob,
                List.of(player(HealthBand.HEALTHY, TargetCue.SHIELD_DISABLED))));
        var other = new ParticipantFacts(new ParticipantAlias(2), HealthBand.CRITICAL, ArmorBand.LIGHT,
                Set.of(TargetCue.BLOCKING), 1);
        assertEquals(Set.of(UseCondition.SELF_LOW_HEALTH), ActiveUseConditions.of(mob, List.of(other)));
        assertThrows(IllegalArgumentException.class, () -> ActiveUseConditions.of(mob, other));
        assertThrows(IllegalArgumentException.class, () -> ActiveUseConditions.of(mob(HealthBand.HEALTHY,
                Optional.empty()), player(HealthBand.HEALTHY)));
    }

    private static Set<UseCondition> conditions(DistanceBand distance, Trend trend, boolean grouped) {
        return ActiveUseConditions.of(mob(HealthBand.HEALTHY, Optional.of(relation(distance, trend, grouped))),
                List.of(player(HealthBand.HEALTHY)));
    }

    private static TargetRelation relation(DistanceBand distance, Trend trend, boolean grouped) {
        return new TargetRelation(TARGET, distance, trend, true, true, false, false, grouped);
    }

    private static MobFacts mob(HealthBand health, Optional<TargetRelation> target) {
        return new MobFacts(MOB, health, target, Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static ParticipantFacts player(HealthBand health, TargetCue... cues) {
        return new ParticipantFacts(TARGET, health, ArmorBand.MEDIUM, Set.of(cues), 1);
    }
}
