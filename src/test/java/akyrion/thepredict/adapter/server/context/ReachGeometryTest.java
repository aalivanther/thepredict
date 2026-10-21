package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReachMeasure;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachGeometryTest {
    private static final BoundingBox CASTER = new BoundingBox(0, 0, 0, 1, 2, 1);

    @Test
    void meleeAndConeCompareTheGapBetweenBoxes() {
        assertTrue(within(purpose(Delivery.MELEE, 2.5, 0), 3.5));
        assertFalse(within(purpose(Delivery.MELEE, 2.5, 0), 3.5001));
        assertTrue(within(purpose(Delivery.CONE, 4, 0), 5));
        assertFalse(within(purpose(Delivery.CONE, 4, 0), 5.0001));
    }

    @Test
    void anAreaAroundTheCasterUsesItsRadiusAsTheMythicMobsTargeterUsesR() {
        var slam = purpose(Delivery.AREA_AROUND_SELF, 0, 3);
        assertTrue(within(slam, 2.9999), "strictly closer than r, as @PlayersInRadius");
        assertFalse(within(slam, 3), "at exactly r @PlayersInRadius misses, so ThePredict waits");
        assertFalse(within(slam, 3.0001));
        assertFalse(within(slam, 3.5), "a gap of 2.5 between boxes is not enough: the positions are 3.5 apart");
        assertTrue(ReachGeometry.withinRadius(8.9999, 3));
        assertFalse(ReachGeometry.withinRadius(9, 3));
    }

    @Test
    void rangedDeliveriesWithReachCompareTheDistanceBetweenCentres() {
        for (var delivery : new Delivery[] {Delivery.PROJECTILE, Delivery.HITSCAN, Delivery.AREA_AT_TARGET}) {
            var radius = delivery == Delivery.AREA_AT_TARGET ? 3 : 0;
            assertTrue(within(purpose(delivery, 12, radius), 12), delivery.name());
            assertFalse(within(purpose(delivery, 12, radius), 12.1), delivery.name());
        }
    }

    @Test
    void selfAndRangedDeliveriesWithoutReachAlwaysReach() {
        assertTrue(within(purpose(Delivery.SELF, 0, 0), 200));
        assertTrue(within(purpose(Delivery.PROJECTILE, 0, 0), 200));
    }

    @Test
    void theGapIsZeroForTouchingBoxesAndTheCentreDistanceIsSquared() {
        assertEquals(0.0, ReachGeometry.gapBlocks(CASTER, new BoundingBox(1, 0, 0, 2, 2, 1)));
        assertEquals(0.0, ReachGeometry.gapBlocks(CASTER, new BoundingBox(0.5, 1, 0.5, 3, 3, 3)));
        assertEquals(144.0, ReachGeometry.centreDistanceSquared(CASTER, at(12)));
        assertTrue(ReachGeometry.measuresGap(Delivery.CONE));
        assertFalse(ReachGeometry.measuresGap(Delivery.AREA_AROUND_SELF));
        assertFalse(ReachGeometry.measuresGap(Delivery.AREA_AT_TARGET));
    }

    private static boolean within(SkillPurpose purpose, double x) {
        return ReachGeometry.withinReach(purpose, CASTER, at(x), x * x);
    }

    private static BoundingBox at(double x) {
        return new BoundingBox(x, 0, 0, x + 1, 2, 1);
    }

    private static SkillPurpose purpose(Delivery delivery, double reach, double radius) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery,
                reach > 0 ? OptionalDouble.of(reach) : OptionalDouble.empty(),
                radius > 0 ? OptionalDouble.of(radius) : OptionalDouble.empty(), 0, CooldownClass.UNKNOWN,
                DamageTier.LOW, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
    }

    @Test
    void eachMeasureKeepsItsBoundary() {
        assertTrue(within(measured(Delivery.MELEE, 2.5, PurposeOrigin.DECLARED, ReachMeasure.BOX_GAP), 3.5));
        assertFalse(within(measured(Delivery.MELEE, 2.5, PurposeOrigin.DECLARED, ReachMeasure.BOX_GAP), 3.5001));
        assertTrue(within(measured(Delivery.HITSCAN, 16, PurposeOrigin.DECLARED, ReachMeasure.CENTRES), 16));
        assertFalse(within(measured(Delivery.HITSCAN, 16, PurposeOrigin.DECLARED, ReachMeasure.CENTRES), 16.0001));
        var strict = measured(Delivery.HITSCAN, 16, PurposeOrigin.SKILL, ReachMeasure.POSITIONS_STRICT);
        assertTrue(within(strict, 16 - 1.0e-4));
        assertFalse(within(strict, 16));
        var positions = measured(Delivery.CONE, 8, PurposeOrigin.SKILL, ReachMeasure.POSITIONS);
        assertTrue(within(positions, 8));
        assertFalse(within(positions, 8.0001));
    }

    @Test
    void aSkillOriginHitscanOfTwentyCountsTowardTheRivalReach() {
        var id = new ChoiceId("bolt");
        var tactic = new TacticId("hold");
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(tactic, new Tactic(tactic, Movement.HOLD, Optional.of(id), Optional.empty(), 10));
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(id, new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, Optional.of("Bolt"), Set.of(), true, 0,
                measured(Delivery.HITSCAN, 20, PurposeOrigin.SKILL, ReachMeasure.POSITIONS_STRICT)));
        var profile = new TacticalProfile("skill_reach", "Skill", "Hold", tactics, choices, List.of(),
                MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                List.of(), new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
        assertEquals(20.0, ReachGeometry.profileReachBlocks(profile));
    }

    private static SkillPurpose measured(Delivery delivery, double reach, PurposeOrigin origin, ReachMeasure measure) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery, OptionalDouble.of(reach),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(), Set.of(), false, origin,
                measure);
    }

    @Test
    void aRivalBoundToThePredictReachesAsFarAsItsLongestDamagingSkillOrItsMeleeReach() {
        assertEquals(12.0, ReachGeometry.profileReachBlocks(ContextFixtures.declaredProfile()),
                "the declared hitscan of 12 blocks is the longest damaging reach");
        assertEquals(2.5, ReachGeometry.profileReachBlocks(ContextFixtures.profile(List.of())),
                "inferred purposes do not count: the melee reach of the profile");
    }
}
