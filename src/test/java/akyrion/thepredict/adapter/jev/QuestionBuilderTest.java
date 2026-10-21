package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_A;
import static akyrion.thepredict.adapter.jev.JevFixtures.guardian;
import static akyrion.thepredict.adapter.jev.JevFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionBuilderTest {
    /**
     * Length of the longest option: {@code FLANK}, the three longest roles, {@code AREA_AT_TARGET} with reach and area,
     * a long wind-up, every piece of the skill, the four longest conditions and risks and 200 description characters.
     */
    static final int MAX_OPTION_CHARS = 901;
    static final int MAX_FULL_OPTION_CHARS = 1211;

    @Test
    void eachSituationHasItsInstructions() {
        assertEquals("Which tactic should mob_2 use now against its current target?",
                instructions(Situation.NOW));
        assertEquals("Which tactic should mob_2 use if its target enters melee reach?",
                instructions(Situation.IF_TARGET_IN_MELEE));
        assertEquals("Which tactic should mob_2 use if its own health becomes critical?",
                instructions(Situation.IF_OWN_HEALTH_CRITICAL));
    }

    @Test
    void aQuestionMatchesTheChoiceQuestionShapeWithTacticIdsAsCriteriaKeys() {
        var question = legacy(spec(Situation.NOW, "hold_ground", "press_attack", "keep_range"),
                guardian(), "mob_1");
        assertEquals(Set.of("type", "instructions", "criteria"), question.keySet());
        assertEquals("choice", question.get("type").getAsString());
        var criteria = question.getAsJsonObject("criteria");
        assertEquals(List.of("hold_ground", "press_attack", "keep_range"), List.copyOf(criteria.keySet()));
        criteria.entrySet().forEach(entry -> assertTrue(entry.getValue().getAsJsonPrimitive().isString()));
    }

    @Test
    void inferredPurposesAreDescribedWithoutWindUpNorCooldownAndKeepTheDescriptionLast() {
        var profile = guardian();
        assertEquals("stay between the inner and outer distance band; uses a skill that fires a projectile at the target"
                        + " to deal damage, deals medium damage; best when the target is outside melee reach; Shoot while"
                        + " the target cannot reach you",
                ControlArmQuestions.criterion(profile.tactic(tactic("keep_range")).orElseThrow(), profile));
        assertEquals("move toward the target and fight up close; uses a skill that strikes the target up close to deal"
                        + " damage, reaching only inside melee reach, deals damage; best when the target is inside melee"
                        + " reach", ControlArmQuestions.criterion(profile.tactic(tactic("press_attack")).orElseThrow(), profile));
        assertEquals("retreat away from the target; uses a skill that acts only on the caster to restore the caster's"
                        + " health and to protect the caster from damage; best when the caster's health is critical",
                ControlArmQuestions.criterion(profile.tactic(tactic("defend_self")).orElseThrow(), profile));
        assertEquals("hold position", ControlArmQuestions.criterion(profile.tactic(tactic("hold_ground")).orElseThrow(),
                profile));
    }

    @Test
    void anUntypedChoiceOmitsThePurposeClause() {
        var base = guardian();
        var choices = new LinkedHashMap<>(base.choices());
        var volley = choices.get(new ChoiceId("volley")).withoutPurpose();
        choices.put(volley.id(), volley);
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), choices,
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
        var text = ControlArmQuestions.criterion(profile.tactic(tactic("keep_range")).orElseThrow(), profile);
        assertEquals("stay between the inner and outer distance band; Shoot while the target cannot reach you", text);
        assertFalse(text.contains("uses a skill"));
    }

    @Test
    void theFourDeclaredChoicesOfTheExampleAreDescribedExactly() {
        var example = ScenarioFacts.example();
        assertEquals("move toward the target and fight up close; uses a skill that strikes the target up close to deal"
                + " damage, reaching only inside melee reach, after a short wind-up, deals medium damage; best when the"
                + " target is inside melee reach", criterion(example, "press_and_strike"));
        assertEquals("stay between the inner and outer distance band; uses a skill that hits the target instantly from"
                + " a distance to deal damage, reaching within ranged reach, after a short wind-up, deals low damage;"
                + " best when the target is backing away or the target is outside melee reach; risk: needs a clear line"
                + " of sight; Stay outside melee reach and shoot while the target approaches",
                criterion(example, "keep_distance_and_shoot"));
        assertEquals("retreat away from the target; uses a skill that acts only on the caster to restore the caster's"
                + " health, at once; best when the caster's health is critical", criterion(example, "retreat_and_defend"));
        assertEquals("move toward the target and fight up close; uses a skill that hits every enemy around the caster"
                + " to stun, slow or push the target and to deal damage, in a small area, after a long, visible wind-up,"
                + " deals medium damage; best when the target stands close to other enemies or the target is inside"
                + " melee reach; risk: the caster cannot move while using it", criterion(example, "close_in_and_slam"));
    }

    @Test
    void noCriterionOfTheExampleHasADigitAndEveryPairOfTacticsReadsDifferently() {
        var example = ScenarioFacts.example();
        var criteria = new ArrayList<String>();
        for (var tactic : example.tactics().values()) {
            var text = ControlArmQuestions.criterion(tactic, example);
            assertFalse(text.matches(".*[0-9].*"), text);
            assertFalse(criteria.contains(text), tactic.id().value());
            criteria.add(text);
        }
        assertEquals(8, criteria.size());
    }

    @Test
    void theResolvedCooldownClassAndAShieldBreakerAreSaid() {
        var example = ScenarioFacts.example();
        var choices = new LinkedHashMap<>(example.choices());
        var melee = choices.get(new ChoiceId("melee"));
        choices.put(melee.id(), melee.withPurpose(melee.typedPurpose().withCooldown(CooldownClass.MEDIUM).ignoringShields()));
        var profile = new TacticalProfile(example.id(), example.mythicMobId(), example.objective(), example.tactics(),
                choices, example.contexts(), example.movement(), example.health(), example.threat(), example.baseAi(),
                example.timing());
        assertTrue(criterion(profile, "press_and_strike").contains(
                "deals medium damage, has a moderate cooldown, goes through shields; best when"));
    }

    @Test
    void theLongestPossibleOptionIsMeasured() {
        var profile = JevFixtures.worstCaseProfile(1);
        var tactic = profile.tactic(tactic("worst_case_000")).orElseThrow();
        assertEquals(MAX_OPTION_CHARS, ControlArmQuestions.criterion(tactic, profile).length());
    }

    private static String criterion(TacticalProfile profile, String tactic) {
        return ControlArmQuestions.criterion(profile.tactic(tactic(tactic)).orElseThrow(), profile);
    }

    @Test
    void oneOptionIsRejectedBeforeSending() {
        var exception = assertThrows(SystemOneRequestException.class, () -> ControlArmQuestions.build(Situation.NOW,
                List.of(tactic("hold_ground")), guardian(), "mob_1"));
        assertEquals("a choice question needs between 2 and 255 options, had 1", exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> JevFixtures.question(0, MOB_A, Situation.NOW, "hold_ground"));
    }

    @Test
    void exactly255OptionsAreAcceptedAnd256Rejected() {
        var profile = wideProfile(256);
        var options = new ArrayList<>(profile.tactics().keySet());
        var accepted = ControlArmQuestions.build(Situation.NOW, options.subList(0, 255), profile, "mob_1");
        assertEquals(255, accepted.getAsJsonObject("criteria").size());
        assertThrows(SystemOneRequestException.class, () -> ControlArmQuestions.build(Situation.NOW, options, profile, "mob_1"));
        assertThrows(IllegalArgumentException.class, () -> new QuestionSpec(QuestionKey.of(0), MOB_A, Situation.NOW, options));
    }

    @Test
    void anOptionTheProfileDoesNotDeclareIsRejected() {
        var exception = assertThrows(SystemOneRequestException.class, () -> legacy(
                spec(Situation.NOW, "hold_ground", "summon_minions"), guardian(), "mob_1"));
        assertEquals("option summon_minions is not declared by the profile of mob_1", exception.getMessage());
    }

    @Test
    void aRepeatedOptionIsRejected() {
        assertThrows(SystemOneRequestException.class, () -> ControlArmQuestions.build(Situation.NOW,
                List.of(tactic("hold_ground"), tactic("hold_ground")), guardian(), "mob_1"));
    }

    private static String instructions(Situation situation) {
        JsonObject question = legacy(spec(situation, "hold_ground", "press_attack"), guardian(), "mob_2");
        return question.get("instructions").getAsString();
    }

    private static JsonObject legacy(QuestionSpec spec, TacticalProfile profile, String mobRef) {
        return ControlArmQuestions.build(spec.situation(), spec.options(), profile, mobRef);
    }

    private static QuestionSpec spec(Situation situation, String... options) {
        return JevFixtures.question(0, MOB_A, situation, options);
    }

    private static TacticalProfile wideProfile(int count) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        for (var index = 0; index < count; index++) {
            var id = tactic(String.format("tactic_%03d", index));
            tactics.put(id, new Tactic(id, Movement.HOLD, Optional.empty(), Optional.empty(), 50));
        }
        return JevFixtures.profile(tactics, "Hold the line");
    }
}
