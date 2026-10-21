package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.domain.DomainFixtures.ENCOUNTER;
import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.guardian;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionRequestTest {
    private static final CombatFacts FACTS = new CombatFacts(ENCOUNTER, 10, 0,
            List.of(new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of())),
            List.of(), List.of(), 0);

    private static QuestionSpec question(int index, MobKey mob) {
        return new QuestionSpec(QuestionKey.of(index), mob, Situation.NOW,
                List.of(tactic("press_attack"), tactic("keep_range")));
    }

    private static String error(EncounterId encounter, List<QuestionSpec> questions, Map<MobKey, TacticalProfile> profiles) {
        return assertThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(encounter, 0, FACTS, questions, profiles)).getMessage();
    }

    @Test
    void validRequestKeepsItsQuestionsAndProfiles() {
        var request = new DecisionRequest(ENCOUNTER, 3, FACTS, List.of(question(0, MOB)), Map.of(MOB, guardian()));
        assertEquals(1, request.questions().size());
        assertEquals(guardian(), request.profiles().get(MOB));
    }

    @Test
    void questionsMustNotBeEmpty() {
        assertTrue(error(ENCOUNTER, List.of(), Map.of(MOB, guardian())).contains("questions"));
    }

    @Test
    void questionKeysMustBeUnique() {
        assertTrue(error(ENCOUNTER, List.of(question(0, MOB), question(0, MOB)), Map.of(MOB, guardian()))
                .contains("q0"));
    }

    @Test
    void askedMobsMustBePartOfTheFacts() {
        var stranger = new MobKey(UUID.randomUUID(), 0);
        assertTrue(error(ENCOUNTER, List.of(question(0, stranger)), Map.of(stranger, guardian())).contains("facts"));
    }

    @Test
    void askedMobsMustHaveAProfile() {
        assertTrue(error(ENCOUNTER, List.of(question(0, MOB)), Map.of()).contains("profiles"));
    }

    @Test
    void factsMustBelongToTheEncounter() {
        assertTrue(error(new EncounterId(2), List.of(question(0, MOB)), Map.of(MOB, guardian())).contains("encounter"));
    }

    @Test
    void negativeSequenceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DecisionRequest(ENCOUNTER, -1, FACTS,
                List.of(question(0, MOB)), Map.of(MOB, guardian())));
    }

    @Test
    void fitChecksShareTheKeysOfTheQuestionsAndNameAnOptionOfTheNowQuestionOfTheirMob() {
        var questions = List.of(question(0, MOB));
        var check = new FitCheck(QuestionKey.of(1), MOB, tactic("keep_range"));
        var request = new DecisionRequest(ENCOUNTER, 3, FACTS, questions, Map.of(MOB, guardian()), List.of(check));
        assertEquals(List.of(check), request.fitChecks());
        assertEquals(List.of(), new DecisionRequest(ENCOUNTER, 3, FACTS, questions, Map.of(MOB, guardian())).fitChecks());
        assertThrows(IllegalArgumentException.class, () -> new DecisionRequest(ENCOUNTER, 3, FACTS, questions,
                Map.of(MOB, guardian()), List.of(new FitCheck(QuestionKey.of(0), MOB, tactic("keep_range")))));
        assertThrows(IllegalArgumentException.class, () -> new DecisionRequest(ENCOUNTER, 3, FACTS, questions,
                Map.of(MOB, guardian()), List.of(new FitCheck(QuestionKey.of(1), MOB, tactic("hold_ground")))));
        var contingency = new QuestionSpec(QuestionKey.of(0), MOB, Situation.IF_TARGET_IN_MELEE,
                List.of(tactic("press_attack"), tactic("keep_range")));
        assertThrows(IllegalArgumentException.class, () -> new DecisionRequest(ENCOUNTER, 3, FACTS,
                List.of(contingency), Map.of(MOB, guardian()), List.of(check)));
    }
}
