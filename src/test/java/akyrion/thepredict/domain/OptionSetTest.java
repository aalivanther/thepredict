package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.declared;
import static akyrion.thepredict.domain.DomainFixtures.meleePurpose;
import static akyrion.thepredict.domain.DomainFixtures.profile;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static akyrion.thepredict.domain.DomainFixtures.tacticOf;
import static akyrion.thepredict.domain.DomainFixtures.timing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionSetTest {
    private static TacticalProfile twins() {
        var strike = declared("strike", meleePurpose(2.5));
        var twin = declared("twin", new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(),
                Set.of(), false, PurposeOrigin.SKILL));
        var heavy = declared("heavy", new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.HIGH, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED));
        return profile(List.of(tacticOf("press", Movement.APPROACH, "strike", 60),
                        tacticOf("press_again", Movement.APPROACH, "twin", 50),
                        tacticOf("press_hard", Movement.APPROACH, "heavy", 40),
                        tacticOf("flank", Movement.FLANK, "strike", 30),
                        tacticOf("approach", Movement.APPROACH, null, 20),
                        tacticOf("close_in", Movement.APPROACH, null, 10),
                        new Tactic(tactic("close_in_quietly"), Movement.APPROACH, Optional.empty(),
                                Optional.of("Walk in without a sound"), 5)),
                List.of(strike, twin, heavy), List.of(), timing());
    }

    private static List<TacticId> ids(String... names) {
        return List.of(names).stream().map(TacticId::new).toList();
    }

    @Test
    void onlyTacticsIdenticalInEverythingAreMergedKeepingTheFirstInOrder() {
        var profile = twins();
        var ordered = ids("press", "press_again", "press_hard", "flank", "approach", "close_in", "close_in_quietly");
        assertEquals(ids("press", "press_hard", "flank", "approach", "close_in_quietly"),
                OptionSet.distinct(ordered, profile));
        assertTrue(OptionSet.identical(profile, tactic("press"), tactic("press_again")),
                "purposes that differ only in their origin are semantically equal");
        assertFalse(OptionSet.identical(profile, tactic("press"), tactic("press_hard")), "another damage tier");
        assertFalse(OptionSet.identical(profile, tactic("press"), tactic("flank")), "another movement");
        assertFalse(OptionSet.identical(profile, tactic("approach"), tactic("close_in_quietly")), "another description");
        assertFalse(OptionSet.identical(profile, tactic("press"), tactic("approach")), "a choice against none");
    }

    @Test
    void theTacticToKeepSurvivesTheMergeOfItsGroupInItsOwnPlace() {
        var ordered = ids("press", "press_again", "approach", "close_in");
        assertEquals(ids("press_again", "approach"), OptionSet.distinct(ordered, twins(), tactic("press_again")));
        assertEquals(ids("press", "close_in"), OptionSet.distinct(ordered, twins(), tactic("close_in")));
    }

    @Test
    void theCapCutsInOrderButNeverDropsTheTacticToKeep() {
        var distinct = ids("press", "press_hard", "flank", "approach", "close_in_quietly");
        assertEquals(ids("press", "press_hard"), OptionSet.capped(distinct, 2));
        assertEquals(ids("press", "close_in_quietly"), OptionSet.capped(distinct, 2, tactic("close_in_quietly")));
        assertEquals(ids("press", "press_hard"), OptionSet.capped(distinct, 2, tactic("press_hard")));
        assertEquals(distinct, OptionSet.capped(distinct, 32, tactic("flank")));
        assertEquals(ids("press", "press_hard"), OptionSet.capped(distinct, 2, tactic("not_offered")));
        assertThrows(IllegalArgumentException.class, () -> OptionSet.capped(distinct, 1));
    }

    @Test
    void aProfileOfTwoHundredFiftyFiveDistinctTacticsIsCappedAtTheDefaultEight() {
        var tactics = new ArrayList<Tactic>();
        for (var index = 0; index < 255; index++) {
            tactics.add(new Tactic(tactic(String.format("tactic_%03d", index)), Movement.HOLD, Optional.empty(),
                    Optional.of("Variant " + index), 50));
        }
        var profile = profile(tactics, List.of(), List.of(), timing());
        var ordered = List.copyOf(profile.tactics().keySet());
        var capped = OptionSet.capped(OptionSet.distinct(ordered, profile), OptionSet.DEFAULT_MAX_OPTIONS);
        assertEquals(8, capped.size());
        assertEquals(ordered.subList(0, 8), capped);
        assertEquals(2, OptionSet.MIN_MAX_OPTIONS);
        assertEquals(32, OptionSet.MAX_MAX_OPTIONS);
    }

    @Test
    void anUndeclaredTacticIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> OptionSet.distinct(ids("press", "summon"), twins()));
    }
}
