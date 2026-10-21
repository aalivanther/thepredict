package akyrion.thepredict.adapter.server.context;

import java.util.Optional;
import java.util.Objects;
import java.nio.file.Files;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static akyrion.thepredict.adapter.server.context.ContextFixtures.GUARD;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.SLAM;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.PUNCH;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.STRIKE;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.profile;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadyTacticsTest {
    private static List<String> names(Set<TacticId> tactics) {
        return tactics.stream().map(TacticId::value).toList();
    }

    @Test
    void aBrainSpeciesOnlyKeepsTacticsWithoutMovement() {
        assertEquals(List.of("stand"), names(ReadyTactics.eligible(profile(List.of()), Set.of(), true, true, true,
                choice -> true)));
    }

    @Test
    void withoutATargetOnlyHoldAndNoneRemainAndTargetChoicesAreNotReady() {
        assertEquals(EnumSet.of(Movement.HOLD, Movement.NONE), ReadyTactics.availableMovements(false, false, true));
        assertEquals(Set.of(GUARD), ReadyTactics.readyChoices(profile(List.of()), false, choice -> true));
        assertEquals(List.of("hold", "stand"), names(ReadyTactics.eligible(profile(List.of()), Set.of(), false, false,
                false, choice -> true)));
    }

    @Test
    void flankIsEligibleOnlyWithAFlankableTarget() {
        assertFalse(ReadyTactics.availableMovements(false, true, false).contains(Movement.FLANK));
        assertTrue(ReadyTactics.availableMovements(false, true, true).contains(Movement.FLANK));
        assertFalse(ReadyTactics.eligible(profile(List.of()), Set.of(), false, true, false, choice -> true)
                .contains(tactic("flank_and_strike")));
        assertTrue(ReadyTactics.eligible(profile(List.of()), Set.of(), false, true, true, choice -> true)
                .contains(tactic("flank_and_strike")));
    }

    @Test
    void aSkillOnItsNativeCooldownRemovesItsTacticsAndAVanillaChoiceIsNeverAskedAboutCooldown() {
        var asked = new ArrayList<ChoiceId>();
        var ready = ReadyTactics.readyChoices(profile(List.of()), true, choice -> {
            asked.add(choice);
            return !choice.equals(STRIKE);
        });
        assertEquals(Set.of(GUARD, PUNCH), ready);
        assertFalse(asked.contains(PUNCH));
        var eligible = ReadyTactics.eligible(profile(List.of()), Set.of(), false, true, true,
                choice -> !choice.equals(STRIKE));
        assertEquals(List.of("retreat_and_guard", "approach", "keep_distance", "punch_in_place", "hold", "stand"),
                names(eligible));
    }

    @Test
    void anActiveContextStillRestrictsTheReadyTactics() {
        var profile = profile(List.of(ContextFixtures.rule("wounded", List.of("healthpercent{p=<30%}"), 90,
                "retreat_and_guard", "hold")));
        assertEquals(List.of("retreat_and_guard", "hold"), names(ReadyTactics.eligible(profile,
                Set.of(ContextFixtures.context("wounded")), false, true, false, choice -> true)));
        assertEquals(List.of("hold"), names(ReadyTactics.eligible(profile, Set.of(ContextFixtures.context("wounded")),
                false, true, false, choice -> false)));
    }

    @Test
    void onlyDeclaredChoicesWithAReachAreMeasuredAndMarkedOutOfReach() {
        var declared = ContextFixtures.declaredProfile();
        assertTrue(ReadyTactics.measuresReach(declared));
        assertFalse(ReadyTactics.measuresReach(profile(List.of())));
        var caster = new BoundingBox(0, 0, 0, 1, 2, 1);
        assertEquals(Set.of(), outOfReach(declared, caster, 2));
        assertEquals(Set.of(ContextFixtures.SLAM), outOfReach(declared, caster, 3.5));
        assertEquals(Set.of(STRIKE, ContextFixtures.SLAM), outOfReach(declared, caster, 3.6));
        assertEquals(Set.of(STRIKE, ContextFixtures.SLAM), outOfReach(declared, caster, 4.1));
        assertEquals(Set.of(STRIKE, ContextFixtures.SLAM, ContextFixtures.BOLT), outOfReach(declared, caster, 12.1));
        assertEquals(Set.of(), outOfReach(profile(List.of()), caster, 40));
    }

    @Test
    void theSlamRadiusIsTheDistanceBetweenPositionsLikeTheMythicMobsTargeter() {
        var declared = ContextFixtures.declaredProfile();
        var caster = new BoundingBox(0, 0, 0, 1, 2, 1);
        assertEquals(Set.of(), outOfReach(declared, caster, 2.9999));
        assertEquals(Set.of(ContextFixtures.SLAM), outOfReach(declared, caster, 3));
        assertEquals(Set.of(ContextFixtures.SLAM), outOfReach(declared, caster, 3.0001));
    }

    private static Set<ChoiceId> outOfReach(TacticalProfile profile, BoundingBox caster, double x) {
        return ReadyTactics.outOfReach(profile, caster, new BoundingBox(x, 0, 0, x + 1, 2, 1), x * x);
    }

    @Test
    void aStrictRadiusInsideMeleePlusHalfABoxIsOutUnderTheMeleeHypothesis() {
        assertEquals(Set.of(SLAM), ReadyTactics.outOfReachInMelee(ContextFixtures.declaredProfile()));
        assertFalse(ReadyTactics.outOfReachInMelee(withReach(STRIKE, Delivery.MELEE, 2.5, 0)).contains(STRIKE));
        assertTrue(ReadyTactics.outOfReachInMelee(withReach(STRIKE, Delivery.MELEE, 2.499, 0)).contains(STRIKE));
        assertFalse(ReadyTactics.outOfReachInMelee(withReach(SLAM, Delivery.AREA_AROUND_SELF, 0, 3.1)).contains(SLAM));
        assertTrue(ReadyTactics.outOfReachInMelee(withReach(SLAM, Delivery.AREA_AROUND_SELF, 0, 3.099)).contains(SLAM));
    }

    private static TacticalProfile withReach(ChoiceId id, Delivery delivery, double reach, double radius) {
        var base = ContextFixtures.declaredProfile();
        var choices = new LinkedHashMap<>(base.choices());
        var current = choices.get(id);
        choices.put(id, new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, current.mythicSkillId(), Set.of(),
                current.requiresTarget(), 0, new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery,
                reach > 0 ? OptionalDouble.of(reach) : OptionalDouble.empty(),
                radius > 0 ? OptionalDouble.of(radius) : OptionalDouble.empty(), 0, CooldownClass.UNKNOWN,
                DamageTier.MEDIUM, Set.of(), Set.of(), false, PurposeOrigin.DECLARED)));
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), choices,
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
    }

    @Test
    void keepDistanceWithADeclaredMeleeSkillOutOfReachIsNotEligibleButHoldAndApproachStay() {
        var declared = ContextFixtures.declaredProfile();
        var out = ReadyTactics.eligible(declared, Set.of(), false, true, false, choice -> true, Set.of(STRIKE));
        assertFalse(out.contains(tactic("keep_and_strike")));
        assertTrue(out.containsAll(Set.of(tactic("strike_close"), tactic("hold_and_strike"), tactic("keep_and_shoot"))));
        var in = ReadyTactics.eligible(declared, Set.of(), false, true, false, choice -> true);
        assertTrue(in.contains(tactic("keep_and_strike")));
    }

    private static TacticalProfile example() throws Exception {
        var directory = Files.createTempDirectory("thepredict-example");
        try (var stream = ReadyTacticsTest.class.getResourceAsStream("/examples/AI/ThePredictGuardian.yml")) {
            Files.copy(Objects.requireNonNull(stream), directory.resolve("ThePredictGuardian.yml"));
        }
        var profile = new ProfileLoader().load(directory).profiles().get("ThePredictGuardian");
        Files.delete(directory.resolve("ThePredictGuardian.yml"));
        Files.delete(directory);
        return profile;
    }

    @Test
    void theContingenciesOfTheExampleOfferOnlyWhatTheirHypothesisAllows() throws Exception {
        var profile = example();
        var out = Set.of(new ChoiceId("melee"), new ChoiceId("slam"));
        var eligible = ReadyTactics.contingencyEligible(profile, Set.of(), false, false, choice -> true, out,
                new ShortcutFacts(HealthBand.HEALTHY, Optional.of(DistanceBand.RANGED)));
        assertEquals(List.of("retreat_and_defend", "keep_distance_and_shoot"),
                names(eligible.get(Situation.IF_OWN_HEALTH_CRITICAL)));
        var melee = eligible.get(Situation.IF_TARGET_IN_MELEE);
        assertTrue(melee.contains(tactic("close_in_and_slam")), melee.toString());
        assertTrue(melee.contains(tactic("press_and_strike")));
        assertFalse(melee.contains(tactic("flank_and_strike")), "the flank keeps the current shield: not flankable");
        assertEquals(Set.of(Situation.IF_TARGET_IN_MELEE, Situation.IF_OWN_HEALTH_CRITICAL), eligible.keySet());
    }

    @Test
    void aNativeContextKeepsItsStateUnderAHypothesisAndAShortcutIsEvaluatedAgain() {
        var declared = ContextFixtures.declaredProfile();
        var eligible = ReadyTactics.contingencyEligible(declared, Set.of(), false, true, choice -> true, Set.of(STRIKE),
                new ShortcutFacts(HealthBand.HEALTHY, Optional.of(DistanceBand.RANGED)));
        assertTrue(eligible.get(Situation.IF_TARGET_IN_MELEE).contains(tactic("keep_and_strike")),
                "in melee the declared strike reaches, so keeping distance may cast again");
    }
}
