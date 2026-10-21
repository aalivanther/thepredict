package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.adapter.server.execution.HitChecks.ReachVerdict;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReachMeasure;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M15.6 and M19.2: reach by delivery, and an untyped skill with no label-based reach. */
class HitChecksTest {
    private static final double MELEE_REACH = 2.5;

    private final ExecutionDoubles.Target target = new ExecutionDoubles.Target(ExecutionDoubles.world("world"));
    private final ExecutionDoubles.Caster caster = new ExecutionDoubles.Caster(target.world, 0, 0);

    @Test
    void everySubsetOfLabelsKeepsTheDamageAndMeleeReachRulesOfTheZeroZeroOne() {
        var values = PredictValue.values();
        var compared = 0;
        for (var mask = 0; mask < 1 << values.length; mask++) {
            var subset = EnumSet.noneOf(PredictValue.class);
            for (var index = 0; index < values.length; index++) {
                if ((mask & 1 << index) != 0) {
                    subset.add(values[index]);
                }
            }
            for (var requiresTarget : new boolean[] {true, false}) {
                var choice = new SkillChoice(new ChoiceId("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                        subset, requiresTarget, 0);
                assertEquals(requiresTarget, HitChecks.damaging(choice), subset + " " + requiresTarget);
                caster.z = 3.1;
                assertEquals(ReachVerdict.IN_REACH, reach(choice, caster.entity, target.player, MELEE_REACH),
                        subset.toString());
                caster.z = 3.0;
                assertEquals(ReachVerdict.IN_REACH, reach(choice, caster.entity, target.player, MELEE_REACH));
                compared++;
            }
        }
        assertEquals(8192 * 2, compared);
        assertEquals(0, caster.sightChecks);
    }

    @Test
    void aVanillaMeleeChoiceIsDamagingAndKeepsTheMeleeReachOfTheProfile() {
        var punch = new SkillChoice(new ChoiceId("punch"), ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(), true, 0);
        assertTrue(HitChecks.damaging(punch));
        caster.z = 3.0;
        assertEquals(ReachVerdict.IN_REACH, reach(punch, caster.entity, target.player, MELEE_REACH));
        caster.z = 3.1;
        assertEquals(ReachVerdict.OUT_OF_REACH, reach(punch, caster.entity, target.player, MELEE_REACH));
        assertEquals(ReachVerdict.IN_REACH, reach(punch, caster.entity, target.player, 2.6));
    }

    @Test
    void meleeAndConeWaitUntilTheGapBetweenBoxesIsWithinTheirReach() {
        caster.z = 3.1;
        var gap = gapNow();
        assertEquals(2.5, gap, 1e-9);
        for (var delivery : new Delivery[] {Delivery.MELEE, Delivery.CONE}) {
            var choice = declared(delivery, OptionalDouble.of(gap), OptionalDouble.empty(), Set.of());
            caster.z = 3.1;
            assertEquals(ReachVerdict.IN_REACH, reach(choice, caster.entity, target.player, 1.0));
            caster.z = 3.1001;
            assertEquals(ReachVerdict.OUT_OF_REACH, reach(choice, caster.entity, target.player, 1.0));
        }
    }

    @Test
    void anAreaAroundTheCasterWaitsUntilThePositionsAreCloserThanItsRadiusAsMythicMobsMeasuresR() {
        var slam = declared(Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), OptionalDouble.of(3), Set.of());
        caster.z = 2.9999;
        assertEquals(ReachVerdict.IN_REACH, reach(slam, caster.entity, target.player, MELEE_REACH));
        caster.z = 3;
        assertEquals(ReachVerdict.OUT_OF_REACH, reach(slam, caster.entity, target.player, MELEE_REACH),
                "@PlayersInRadius{r=3} keeps a player strictly closer than 3");
        caster.z = 3.0001;
        assertEquals(ReachVerdict.OUT_OF_REACH, reach(slam, caster.entity, target.player, MELEE_REACH));
        caster.z = 2.7;
        assertEquals(2.1, gapNow(), 1e-9);
        assertEquals(ReachVerdict.IN_REACH, reach(slam, caster.entity, target.player, MELEE_REACH));
        caster.z = 3.5;
        assertEquals(2.9, gapNow(), 1e-9);
        assertEquals(ReachVerdict.OUT_OF_REACH, reach(slam, caster.entity, target.player, MELEE_REACH),
                "a gap of 2.9 between boxes is 3.5 between positions: the targeter would miss");
    }

    @Test
    void rangedDeliveriesWithReachCompareTheDistanceBetweenCentres() {
        for (var delivery : new Delivery[] {Delivery.PROJECTILE, Delivery.HITSCAN, Delivery.AREA_AT_TARGET}) {
            var radius = delivery == Delivery.AREA_AT_TARGET ? OptionalDouble.of(3) : OptionalDouble.empty();
            var choice = declared(delivery, OptionalDouble.of(12), radius, Set.of());
            caster.z = 12;
            assertEquals(ReachVerdict.IN_REACH, reach(choice, caster.entity, target.player, MELEE_REACH));
            caster.z = 12.1;
            assertEquals(ReachVerdict.OUT_OF_REACH, reach(choice, caster.entity, target.player, MELEE_REACH));
        }
    }

    @Test
    void selfAndRangedDeliveriesWithoutReachNeverWait() {
        caster.z = 40;
        var self = new SkillChoice(new ChoiceId("guard"), ChoiceKind.MYTHIC_SKILL, Optional.of("Guard"), Set.of(), true,
                0, new SkillPurpose(SkillRole.DEFENSE, Set.of(SkillRole.DEFENSE), Delivery.SELF, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED));
        assertEquals(ReachVerdict.IN_REACH, reach(self, caster.entity, target.player, MELEE_REACH));
        var projectile = declared(Delivery.PROJECTILE, OptionalDouble.empty(), OptionalDouble.empty(), Set.of());
        assertEquals(ReachVerdict.IN_REACH, reach(projectile, caster.entity, target.player, MELEE_REACH));
    }

    @Test
    void theLineOfSightIsCheckedOnlyWhenDeclaredAndOnlyAfterTheReach() {
        var sighted = declared(Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(),
                Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT));
        caster.z = 16.5;
        caster.sight = false;
        assertEquals(ReachVerdict.OUT_OF_REACH, reach(sighted, caster.entity, target.player, MELEE_REACH));
        assertEquals(0, caster.sightChecks, "no line of sight when the reach already fails");
        caster.z = 8;
        assertEquals(ReachVerdict.NO_LINE_OF_SIGHT, reach(sighted, caster.entity, target.player, MELEE_REACH));
        caster.sight = true;
        assertEquals(ReachVerdict.IN_REACH, reach(sighted, caster.entity, target.player, MELEE_REACH));
        assertEquals(2, caster.sightChecks);
        var blind = declared(Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(), Set.of());
        caster.sight = false;
        assertEquals(ReachVerdict.IN_REACH, reach(blind, caster.entity, target.player, MELEE_REACH));
        assertEquals(2, caster.sightChecks);
    }

    @Test
    void aNativeLineOfSightIsRequiredBeforeTheCastEvenWithoutTheRisk() {
        var nativeSight = declared(Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(), Set.of())
                .withNativeFacts(new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL,
                        OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                        false, false, true, true));
        caster.z = 16.5;
        caster.sight = false;
        assertEquals(ReachVerdict.OUT_OF_REACH, reach(nativeSight, caster.entity, target.player, MELEE_REACH));
        assertEquals(0, caster.sightChecks);
        caster.z = 8;
        assertEquals(ReachVerdict.NO_LINE_OF_SIGHT, reach(nativeSight, caster.entity, target.player,
                MELEE_REACH));
        assertTrue(HitChecks.inReach(nativeSight, caster.entity, target.player, MELEE_REACH),
                "cheap reach does not read the line of sight");
        caster.sight = true;
        assertEquals(ReachVerdict.IN_REACH, reach(nativeSight, caster.entity, target.player, MELEE_REACH));
        var untyped = new SkillChoice(new ChoiceId("raw"), ChoiceKind.MYTHIC_SKILL, Optional.of("Raw"), Set.of(), true,
                0).withNativeFacts(nativeSight.nativeFacts());
        caster.sight = false;
        assertEquals(ReachVerdict.NO_LINE_OF_SIGHT, reach(untyped, caster.entity, target.player, MELEE_REACH));
    }

    @Test
    void aDeclaredPurposeIsDamagingOnlyWhenItNeedsATargetDealsDamageAndIsAimedAtIt() {
        assertTrue(HitChecks.damaging(declared(Delivery.MELEE, OptionalDouble.of(2), OptionalDouble.empty(), Set.of())));
        assertFalse(HitChecks.damaging(declared(Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), OptionalDouble.of(3),
                Set.of())), "an area around the caster is not aimed at the target");
        var untargeted = declared(Delivery.HITSCAN, OptionalDouble.empty(), OptionalDouble.empty(), Set.of());
        assertFalse(HitChecks.damaging(new SkillChoice(untargeted.id(), untargeted.kind(), untargeted.mythicSkillId(),
                Set.of(), false, 0, untargeted.typedPurpose())));
        var debuff = new SkillChoice(new ChoiceId("hex"), ChoiceKind.MYTHIC_SKILL, Optional.of("Hex"),
                Set.of(PredictValue.MELEE_ATTACK), true, 0, new SkillPurpose(SkillRole.DEBUFF, Set.of(SkillRole.DEBUFF),
                Delivery.HITSCAN, OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN,
                DamageTier.NONE, Set.of(), Set.of(), false, PurposeOrigin.DECLARED));
        assertFalse(HitChecks.damaging(debuff), "the declared purpose decides, not the label");
    }

    @Test
    void positionsReachIncludesTheDistanceBetweenTheEntityLocations() {
        caster.x = 8;
        caster.z = 0;
        var cone = new SkillChoice(new ChoiceId("cone"), ChoiceKind.MYTHIC_SKILL, Optional.of("Cone"), Set.of(), true,
                0, new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.CONE, OptionalDouble.of(8),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), Set.of(), false,
                PurposeOrigin.SKILL, ReachMeasure.POSITIONS));
        assertEquals(ReachVerdict.IN_REACH, reach(cone, caster.entity, target.player, MELEE_REACH));
        caster.x = 8.0001;
        assertEquals(ReachVerdict.OUT_OF_REACH, reach(cone, caster.entity, target.player, MELEE_REACH));
    }

    @Test
    void invulnerabilityIsDeferredUnlessTheNativeFactsIgnoreIt() {
        target.noDamageTicks = 20;
        var plain = declared(Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), Set.of());
        assertTrue(HitChecks.deferInvulnerable(plain, target.player));
        var ignoring = plain.withNativeFacts(new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL,
                OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, true,
                false, true));
        assertFalse(HitChecks.deferInvulnerable(ignoring, target.player));
        target.noDamageTicks = 0;
        assertFalse(HitChecks.deferInvulnerable(plain, target.player));
    }

    private double gapNow() {
        return HitChecks.gapBlocks(caster.entity.getBoundingBox(), target.player.getBoundingBox());
    }

    /** The reach rule with a direct line of sight, as the adapter reads it without a clock. */
    private static ReachVerdict reach(SkillChoice choice, org.bukkit.entity.LivingEntity caster,
                                      org.bukkit.entity.LivingEntity target, double meleeReach) {
        return HitChecks.reach(choice, caster, target, meleeReach, new java.util.UUID(0x4CL, 1), LineOfSight.DIRECT);
    }

    @Test
    void theSightCacheReadsOneRayPerMobAndTickAndStartsAgainWhenFull() {
        var tick = new java.util.concurrent.atomic.AtomicLong(10);
        var cache = new SightCache(akyrion.thepredict.engine.ServerClock.of(tick::get, () -> 0L));
        var mob = new java.util.UUID(0x4CL, 2);
        caster.sight = false;
        assertFalse(cache.sees(mob, caster.entity, target.player));
        caster.sight = true;
        assertFalse(cache.sees(mob, caster.entity, target.player), "the answer of the tick is kept");
        assertEquals(1, caster.sightChecks);
        assertTrue(cache.sees(new java.util.UUID(0x4CL, 3), caster.entity, target.player), "another mob reads its own");
        tick.set(11);
        assertTrue(cache.sees(mob, caster.entity, target.player));
        assertEquals(3, caster.sightChecks);
        for (var index = 0; index < SightCache.MAX_MOBS; index++) {
            cache.sees(new java.util.UUID(0x4DL, index), caster.entity, target.player);
        }
        assertTrue(cache.size() <= SightCache.MAX_MOBS, "the map never grows past its cap");
    }

    private static SkillChoice declared(Delivery delivery, OptionalDouble reach, OptionalDouble radius,
                                        Set<SkillRisk> risks) {
        return new SkillChoice(new ChoiceId("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"), Set.of(), true, 0,
                new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery, reach, radius, 0,
                        CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), risks, false, PurposeOrigin.DECLARED));
    }
}
