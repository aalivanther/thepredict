package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.context;
import static akyrion.thepredict.domain.DomainFixtures.health;
import static akyrion.thepredict.domain.DomainFixtures.movement;
import static akyrion.thepredict.domain.DomainFixtures.profile;
import static akyrion.thepredict.domain.DomainFixtures.skill;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static akyrion.thepredict.domain.DomainFixtures.tacticOf;
import static akyrion.thepredict.domain.DomainFixtures.threat;
import static akyrion.thepredict.domain.DomainFixtures.timing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalProfileTest {
    private static final Tactic HOLD = tacticOf("hold_ground", Movement.HOLD, null, 10);

    private static String error(Runnable construction) {
        return assertThrows(IllegalArgumentException.class, construction::run).getMessage();
    }

    @Test
    void minimalProfileIsValid() {
        var profile = profile(List.of(HOLD), List.of(), List.of(), timing());
        assertEquals(Optional.of(HOLD), profile.tactic(tactic("hold_ground")));
        assertEquals(Optional.empty(), profile.tactic(tactic("run_away")));
    }

    @Test
    void atLeastOneTacticIsRequired() {
        assertTrue(error(() -> profile(List.of(), List.of(), List.of(), timing())).contains("tactics"));
    }

    @Test
    void tacticChoiceMustBeDeclared() {
        assertTrue(error(() -> profile(List.of(tacticOf("press_attack", Movement.APPROACH, "melee", 20)),
                List.of(), List.of(), timing())).contains("choice melee"));
    }

    @Test
    void contextMustReferenceDeclaredTactics() {
        var context = new ContextRule(context("low_health"), List.of("healthpercent{p=<30%}"), 10,
                Set.of(tactic("run_away")), Optional.empty());
        assertTrue(error(() -> profile(List.of(HOLD), List.of(), List.of(context), timing())).contains("allowedTactics"));
    }

    @Test
    void contextIdsMustBeUnique() {
        var context = new ContextRule(context("low_health"), List.of("healthpercent{p=<30%}"), 10,
                Set.of(tactic("hold_ground")), Optional.empty());
        assertTrue(error(() -> profile(List.of(HOLD), List.of(), List.of(context, context), timing())).contains("contexts"));
    }

    @Test
    void reservedTacticsCannotBeDeclared() {
        var none = new Tactic(TacticId.NONE, Movement.NONE, Optional.empty(), Optional.empty(), 0);
        var debug = new Tactic(new TacticId("debug_hold"), Movement.HOLD, Optional.empty(), Optional.empty(), 0);
        assertTrue(error(() -> profile(List.of(none), List.of(), List.of(), timing())).contains("reserved"));
        assertTrue(error(() -> profile(List.of(debug), List.of(), List.of(), timing())).contains("reserved"));
    }

    @Test
    void mapKeysMustMatchTheirValues() {
        var tactics = Map.of(tactic("other_name"), HOLD);
        assertTrue(error(() -> new TacticalProfile("guardian_ai", "Guardian", "Win", tactics, Map.of(), List.of(),
                movement(), health(), threat(), List.of(), timing())).contains("tactics key"));
    }

    @Test
    void blankTextFieldsAreRejectedByName() {
        var tactics = Map.of(HOLD.id(), HOLD);
        assertTrue(error(() -> new TacticalProfile("guardian_ai", "Guardian", " ", tactics, Map.of(), List.of(),
                movement(), health(), threat(), List.of(), timing())).contains("objective"));
        assertTrue(error(() -> new TacticalProfile("guardian_ai", "", "Win", tactics, Map.of(), List.of(),
                movement(), health(), threat(), List.of(), timing())).contains("mythicMobId"));
    }

    @Test
    void declarationOrderIsStableAndCopied() {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        for (var name : List.of("zeta_move", "alpha_move", "mid_move")) {
            tactics.put(tactic(name), tacticOf(name, Movement.HOLD, null, 10));
        }
        var profile = new TacticalProfile("guardian_ai", "Guardian", "Win", tactics, Map.of(), List.of(),
                movement(), health(), threat(), List.of("0 meleeattack"), timing());
        tactics.clear();
        assertEquals(List.of(tactic("zeta_move"), tactic("alpha_move"), tactic("mid_move")),
                List.copyOf(profile.tactics().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> profile.tactics().clear());
    }

    @Test
    void guardianFixtureIsValid() {
        assertEquals(4, DomainFixtures.guardian().tactics().size());
        assertEquals(skill("melee"), DomainFixtures.guardian().choices().get(new ChoiceId("melee")));
    }

    @Test
    void aProfileWithoutParticipantRulesHasThoseOf001AndACopyWithOtherContextsKeepsThem() {
        var profile = DomainFixtures.guardian();
        assertEquals(ParticipantRules.DEFAULT, profile.participants());
        var rules = new ParticipantRules(Set.of(), List.of("kind:MONSTER"), List.of(),
                false);
        var withRules = new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), profile.contexts(), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), profile.timing(), rules);
        assertEquals(rules, withRules.withContexts(List.of()).participants());
        assertTrue(withRules.withContexts(List.of()).contexts().isEmpty());
    }
}
