package akyrion.thepredict.domain;

import akyrion.thepredict.config.ProfileLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.choice;
import static akyrion.thepredict.domain.DomainFixtures.context;
import static akyrion.thepredict.domain.DomainFixtures.guardian;
import static akyrion.thepredict.domain.DomainFixtures.profile;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static akyrion.thepredict.domain.DomainFixtures.tacticOf;
import static akyrion.thepredict.domain.DomainFixtures.timing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlannerTest {
    private static Optional<TacticId> chosen(TacticalProfile profile, Set<ContextId> active, Set<TacticId> eligible) {
        return LocalPlanner.choose(profile, active, eligible).map(Tactic::id);
    }

    @Test
    void lowHealthDirectTacticWinsWhileAttacking() {
        var active = Set.of(context("attacking"), context("low_health"));
        var eligible = Eligibility.eligible(guardian(), active,
                Set.of(choice("melee"), choice("ranged"), choice("defense")), EnumSet.allOf(Movement.class));
        assertEquals(Optional.of(tactic("defend_self")), chosen(guardian(), active, eligible));
    }

    @Test
    void highestPriorityWinsWithoutADirectTactic() {
        assertEquals(Optional.of(tactic("defend_self")), chosen(guardian(), Set.of(),
                Set.of(tactic("hold_ground"), tactic("defend_self"), tactic("press_attack"))));
    }

    @Test
    void equalPrioritiesAreBrokenByDeclarationOrder() {
        var profile = profile(List.of(tacticOf("zeta_move", Movement.HOLD, null, 80),
                tacticOf("beta_move", Movement.HOLD, null, 80), tacticOf("low_move", Movement.HOLD, null, 10)),
                List.of(), List.of(), timing());
        assertEquals(Optional.of(tactic("zeta_move")), chosen(profile, Set.of(),
                Set.of(tactic("low_move"), tactic("beta_move"), tactic("zeta_move"))));
        assertEquals(Optional.of(tactic("press_attack")), chosen(guardian(), Set.of(),
                Set.of(tactic("keep_range"), tactic("press_attack"))));
    }

    @Test
    void ineligibleDirectTacticFallsBackToPriority() {
        assertEquals(Optional.of(tactic("press_attack")), chosen(guardian(), Set.of(context("tracking")),
                Set.of(tactic("press_attack"), tactic("hold_ground"))));
    }

    @Test
    void nothingEligibleChoosesNothing() {
        assertEquals(Optional.empty(), chosen(guardian(), Set.of(context("low_health")), Set.of()));
    }

    @Test
    void aDeclaredBestWhenThatHoldsBreaksATieOfPriorities() {
        var profile = shieldProfile(true);
        var eligible = Set.of(tactic("press"), tactic("break_guard"));
        assertEquals(Optional.of(tactic("break_guard")), LocalPlanner.choose(profile, Set.of(), eligible,
                Set.of(UseCondition.TARGET_SHIELDING, UseCondition.TARGET_IN_MELEE)).map(Tactic::id));
        assertEquals(Optional.of(tactic("press")), LocalPlanner.choose(profile, Set.of(), eligible,
                Set.of(UseCondition.TARGET_IN_MELEE)).map(Tactic::id));
        assertEquals(Optional.of(tactic("press")), chosen(profile, Set.of(), eligible));
        assertEquals(60 + LocalPlanner.BEST_WHEN_BONUS, LocalPlanner.score(profile,
                profile.tactic(tactic("break_guard")).orElseThrow(), Set.of(UseCondition.TARGET_SHIELDING)));
    }

    @Test
    void anInferredPurposeScoresItsPriorityAsInTheZeroZeroOne() {
        var profile = shieldProfile(false);
        var eligible = Set.of(tactic("press"), tactic("break_guard"));
        assertEquals(Optional.of(tactic("press")), LocalPlanner.choose(profile, Set.of(), eligible,
                EnumSet.allOf(UseCondition.class)).map(Tactic::id));
        assertEquals(60, LocalPlanner.score(profile, profile.tactic(tactic("break_guard")).orElseThrow(),
                EnumSet.allOf(UseCondition.class)));
    }

    @Test
    void theDirectTacticStillWinsOverAnyBestWhen() {
        var active = Set.of(context("attacking"), context("low_health"));
        var eligible = Set.of(tactic("defend_self"), tactic("press_attack"));
        assertEquals(Optional.of(tactic("defend_self")), LocalPlanner.choose(guardian(), active, eligible,
                EnumSet.allOf(UseCondition.class)).map(Tactic::id));
    }

    @Test
    void thePurposeShieldTestProfileBreaksTheGuardOfABlockingTargetOnly() throws Exception {
        var directory = Files.createTempDirectory("purpose-shield");
        try (var stream = LocalPlannerTest.class.getResourceAsStream("/profiles/purpose-shield.yml")) {
            Files.copy(Objects.requireNonNull(stream), directory.resolve("purpose-shield.yml"));
        }
        var loaded = new ProfileLoader().load(directory);
        assertEquals(List.of(), loaded.problems());
        var profile = loaded.profiles().get("PurposeShieldMob");
        var eligible = Eligibility.eligible(profile, Set.of(), Set.of(choice("strike"), choice("guard_break")),
                EnumSet.allOf(Movement.class));
        var relation = new TargetRelation(new ParticipantAlias(1), DistanceBand.MELEE, Trend.STEADY, true, true, false,
                true, false);
        var mob = new MobFacts(DomainFixtures.MOB, HealthBand.HEALTHY, Optional.of(relation), Set.of(), eligible,
                Set.of(), Set.of());
        var blocking = new ParticipantFacts(new ParticipantAlias(1), HealthBand.HEALTHY, ArmorBand.MEDIUM,
                Set.of(TargetCue.BLOCKING), 1);
        var open = new ParticipantFacts(new ParticipantAlias(1), HealthBand.HEALTHY, ArmorBand.MEDIUM, Set.of(), 1);
        assertEquals(Optional.of(tactic("break_guard")), LocalPlanner.choose(profile, Set.of(), eligible,
                ActiveUseConditions.of(mob, blocking)).map(Tactic::id));
        assertEquals(Optional.of(tactic("press")), LocalPlanner.choose(profile, Set.of(), eligible,
                ActiveUseConditions.of(mob, open)).map(Tactic::id));
        assertTrue(profile.choices().get(choice("strike")).typedPurpose().bestWhen().isEmpty());
        assertTrue(profile.choices().get(choice("guard_break")).typedPurpose().ignoresShield());
        Files.delete(directory.resolve("purpose-shield.yml"));
        Files.delete(directory);
    }

    private static TacticalProfile shieldProfile(boolean declared) {
        var strike = declared ? DomainFixtures.declared("strike", DomainFixtures.meleePurpose(2.5))
                : DomainFixtures.skill("strike");
        var breaker = declared ? DomainFixtures.declared("guard_break", DomainFixtures.meleePurpose(2.5,
                UseCondition.TARGET_SHIELDING)) : new SkillChoice(choice("guard_break"), ChoiceKind.MYTHIC_SKILL,
                Optional.of("Skill_guard_break"), Set.of(PredictValue.MELEE_ATTACK, PredictValue.IGNORES_SHIELD), true, 0);
        return profile(List.of(tacticOf("press", Movement.APPROACH, "strike", 60),
                tacticOf("break_guard", Movement.APPROACH, "guard_break", 60)), List.of(strike, breaker), List.of(),
                timing());
    }
}
