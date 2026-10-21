package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
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

class EligibilityTest {
    private static final Set<ChoiceId> ALL_READY = Set.of(choice("melee"), choice("ranged"), choice("defense"));
    private static final Set<Movement> ALL_MOVEMENTS = EnumSet.allOf(Movement.class);

    private static List<TacticId> eligible(Set<ContextId> active, Set<ChoiceId> ready, Set<Movement> movements) {
        return List.copyOf(Eligibility.eligible(guardian(), active, ready, movements));
    }

    @Test
    void withoutActiveContextsEveryTacticIsOrderedByPriorityThenDeclaration() {
        assertEquals(List.of(tactic("defend_self"), tactic("press_attack"), tactic("keep_range"), tactic("hold_ground")),
                eligible(Set.of(), ALL_READY, ALL_MOVEMENTS));
    }

    @Test
    void highestPriorityContextWinsAmongSimultaneousContexts() {
        var active = Set.of(context("attacking"), context("low_health"));
        assertEquals(List.of(tactic("defend_self")), eligible(active, ALL_READY, ALL_MOVEMENTS));
        assertEquals(Optional.of(tactic("defend_self")),
                Eligibility.direct(guardian(), active, Set.of(tactic("defend_self"))));
    }

    @Test
    void unreadyChoiceOfTheWinningContextDoesNotFallThroughToOtherContexts() {
        var active = Set.of(context("attacking"), context("low_health"));
        var eligible = Eligibility.eligible(guardian(), active, Set.of(choice("melee"), choice("ranged")), ALL_MOVEMENTS);
        assertTrue(eligible.isEmpty());
        assertEquals(Optional.empty(), Eligibility.direct(guardian(), active, eligible));
    }

    @Test
    void trackingContextAllowsOnlyTheRangedTacticAsDirect() {
        var active = Set.of(context("tracking"));
        var eligible = Eligibility.eligible(guardian(), active, ALL_READY, ALL_MOVEMENTS);
        assertEquals(Set.of(tactic("keep_range")), eligible);
        assertEquals(Optional.of(tactic("keep_range")), Eligibility.direct(guardian(), active, eligible));
    }

    @Test
    void equalContextPrioritiesAreBrokenByDeclarationOrder() {
        var profile = profile(List.of(tacticOf("hold_ground", Movement.HOLD, null, 10),
                        tacticOf("run_away", Movement.RETREAT, null, 10)), List.of(),
                List.of(new ContextRule(context("first"), List.of("a"), 5, Set.of(tactic("hold_ground")), Optional.empty()),
                        new ContextRule(context("second"), List.of("b"), 5, Set.of(tactic("run_away")), Optional.empty())),
                timing());
        assertEquals(Set.of(tactic("hold_ground")),
                Eligibility.eligible(profile, Set.of(context("second"), context("first")), Set.of(), ALL_MOVEMENTS));
        assertEquals(context("first"),
                Eligibility.governingContext(profile, Set.of(context("first"), context("second"))).orElseThrow().id());
    }

    @Test
    void choiceOnCooldownExcludesItsTacticButNotMovementOnlyTactics() {
        assertEquals(List.of(tactic("press_attack"), tactic("keep_range"), tactic("hold_ground")),
                eligible(Set.of(), Set.of(choice("melee"), choice("ranged")), ALL_MOVEMENTS));
        assertEquals(List.of(tactic("hold_ground")), eligible(Set.of(), Set.of(), ALL_MOVEMENTS));
    }

    @Test
    void unavailableMovementExcludesItsTactic() {
        assertEquals(List.of(tactic("press_attack"), tactic("keep_range"), tactic("hold_ground")),
                eligible(Set.of(), ALL_READY, EnumSet.complementOf(EnumSet.of(Movement.RETREAT))));
    }

    @Test
    void directTacticIsReturnedOnlyWhenEligible() {
        var active = Set.of(context("low_health"));
        assertEquals(Optional.empty(), Eligibility.direct(guardian(), active, Set.of(tactic("hold_ground"))));
        assertEquals(Optional.empty(), Eligibility.direct(guardian(), Set.of(), Set.of(tactic("defend_self"))));
    }

    @Test
    void undeclaredActiveContextsAreIgnored() {
        assertEquals(4, eligible(Set.of(context("unknown")), ALL_READY, ALL_MOVEMENTS).size());
    }

    @Test
    void aDeclaredMeleeSkillOutOfReachMakesKeepDistanceIneligibleOnlyWhileOutOfReach() {
        var profile = reachProfile(DomainFixtures.declared("strike", DomainFixtures.meleePurpose(2.5)));
        var ready = Set.of(choice("strike"));
        var out = List.copyOf(Eligibility.eligible(profile, Set.of(), ready, ALL_MOVEMENTS, Set.of(choice("strike"))));
        assertEquals(List.of(tactic("strike_close"), tactic("hold_and_strike")), out);
        var in = List.copyOf(Eligibility.eligible(profile, Set.of(), ready, ALL_MOVEMENTS, Set.of()));
        assertEquals(List.of(tactic("strike_close"), tactic("keep_and_strike"), tactic("hold_and_strike")), in);
    }

    @Test
    void anInferredMeleeSkillStaysEligibleAsInTheZeroZeroOne() {
        var profile = reachProfile(DomainFixtures.skill("strike"));
        var ready = Set.of(choice("strike"));
        var legacy = Eligibility.eligible(profile, Set.of(), ready, ALL_MOVEMENTS);
        assertEquals(legacy, Eligibility.eligible(profile, Set.of(), ready, ALL_MOVEMENTS, Set.of(choice("strike"))));
        assertEquals(3, legacy.size());
    }

    @Test
    void holdAndApproachWithADeclaredSkillOutOfReachStayEligible() {
        var profile = reachProfile(DomainFixtures.declared("strike", DomainFixtures.meleePurpose(2.5)));
        var hold = profile.tactic(tactic("hold_and_strike")).orElseThrow();
        var approach = profile.tactic(tactic("strike_close")).orElseThrow();
        var keep = profile.tactic(tactic("keep_and_strike")).orElseThrow();
        var out = Set.of(choice("strike"));
        assertTrue(!Eligibility.neverCasts(profile, hold, out) && !Eligibility.neverCasts(profile, approach, out));
        assertTrue(Eligibility.neverCasts(profile, keep, out));
        assertTrue(!Eligibility.neverCasts(profile, keep, Set.of()));
    }

    private static TacticalProfile reachProfile(SkillChoice strike) {
        return profile(List.of(tacticOf("strike_close", Movement.APPROACH, "strike", 70),
                        tacticOf("keep_and_strike", Movement.KEEP_DISTANCE, "strike", 60),
                        tacticOf("hold_and_strike", Movement.HOLD, "strike", 50)),
                List.of(strike), List.of(), timing());
    }
}
