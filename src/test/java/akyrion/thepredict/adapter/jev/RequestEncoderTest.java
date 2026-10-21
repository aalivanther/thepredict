package akyrion.thepredict.adapter.jev;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.Set;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.engine.RequestBudget;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.InFlightRequest;
import akyrion.thepredict.engine.EngineSettings;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.config.JevSettings;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static akyrion.thepredict.adapter.jev.JevFixtures.ENCOUNTER;
import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_A;
import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_B;
import static akyrion.thepredict.adapter.jev.JevFixtures.guardian;
import static akyrion.thepredict.adapter.jev.JevFixtures.question;
import static akyrion.thepredict.adapter.jev.JevFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestEncoderTest {
    private final RequestEncoder encoder = new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000));

    @Test
    void aFullGroupPutsG0InTheSameBodyAsTheLeaderQuestions() {
        var epoch = 7L;
        var facts = JevFixtures.meleeFacts().withGroup(GroupRelations.NONE, new GroupBrief(
                GroupQuestions.PLAN_AND_LEADER, List.of(GroupPlan.PRESS_MAIN_TARGET.option(),
                GroupPlan.SURROUND_TARGET.option()), epoch, true));
        var base = JevFixtures.request();
        var request = new DecisionRequest(base.encounter(), base.sequence(), facts, base.questions(), base.profiles());
        var questions = encoder.body(request).getAsJsonObject("questions");
        assertTrue(questions.has("q0"));
        assertTrue(questions.has("q1"));
        assertTrue(questions.has("g0"));
        assertTrue(questions.getAsJsonObject("g0").get("instructions").getAsString().contains("membership " + epoch));
    }

    @Test
    void planOnlySendsTheGroupPlanAloneWhileARequestWithoutAPlanQuestionStillNeedsOneQuestion() {
        var facts = JevFixtures.meleeFacts().withGroup(GroupRelations.NONE, new GroupBrief(GroupQuestions.PLAN_ONLY,
                List.of(GroupPlan.PRESS_MAIN_TARGET.option(), GroupPlan.SURROUND_TARGET.option()), 3L, true));
        var base = JevFixtures.request();
        var planOnly = new DecisionRequest(base.encounter(), base.sequence(), facts, List.of(), Map.of());
        var questions = encoder.body(planOnly).getAsJsonObject("questions");
        assertEquals(java.util.Set.of("g0"), questions.keySet(), "DOC-02: plan_only sends only the group plan");
        assertThrows(IllegalArgumentException.class, () -> new DecisionRequest(base.encounter(), base.sequence(),
                JevFixtures.meleeFacts(), List.of(), Map.of()));
    }

    @Test
    void theCompleteBodyMatchesItsGolden() {
        var request = JevFixtures.request();
        var compact = new DecisionRequest(request.encounter(), request.sequence(), request.facts(), request.questions(),
                Map.of(MOB_A, JevFixtures.detailed(guardian(), QuestionDetail.COMPACT)));
        var pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(encoder.body(compact)) + "\n";
        JevFixtures.keepFresh("golden/request-two-questions.json", pretty);
        assertEquals(SystemOneContractServer.fixture("golden/request-two-questions.json"), pretty);
    }

    @Test
    void theCompactBodyCarriesModelStateAndTheQuestionKeysOfTheSpecs() {
        var body = JsonParser.parseString(encoder.encode(JevFixtures.request())).getAsJsonObject();
        assertEquals(List.of("model", "state", "questions"), List.copyOf(body.keySet()));
        assertEquals("jev-1.13.0", body.get("model").getAsString());
        assertEquals(List.of("q0", "q1"), List.copyOf(body.getAsJsonObject("questions").keySet()));
    }

    @Test
    void questionsReferToTheirMobByCaptureOrder() {
        var request = new DecisionRequest(ENCOUNTER, 1, JevFixtures.threeMobFacts(),
                List.of(question(3, MOB_B, Situation.IF_TARGET_IN_MELEE, "defend_self", "press_attack")),
                Map.of(MOB_B, JevFixtures.detailed(guardian(), QuestionDetail.COMPACT)));
        var question = encoder.body(request).getAsJsonObject("questions").getAsJsonObject("q3");
        assertEquals("If mob_2's target comes inside melee reach soon, which tactic then?",
                question.get("instructions").getAsString());
    }

    @Test
    void anUndeclaredOptionStopsTheWholeRequest() {
        var request = new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(),
                List.of(question(0, MOB_A, Situation.NOW, "hold_ground", "summon_minions")), Map.of(MOB_A, guardian()));
        assertThrows(SystemOneRequestException.class, () -> encoder.encode(request));
    }

    @Test
    void aProfileWithoutAQuestionDoesNotLeakItsObjective() {
        var request = new DecisionRequest(ENCOUNTER, 1, JevFixtures.threeMobFacts(),
                List.of(question(0, MOB_A, Situation.NOW, "hold_ground", "press_attack")),
                Map.of(MOB_A, guardian(), MOB_B, JevFixtures.profile(guardian().tactics(), "Secret plan of mob two")));
        var body = encoder.encode(request);
        assertFalse(body.contains("Secret plan of mob two"));
        assertTrue(body.contains("Protect the altar and defeat intruders"));
    }

    @Test
    void theTokenGuardAcceptsTheExactLimitsAndRejectsOneCharacterMore() {
        RequestSizeGuard.check(44_000, 4_000, 96_000);
        assertThrows(SystemOneRequestException.class, () -> RequestSizeGuard.check(44_000, 4_001, 80_000));
        assertThrows(SystemOneRequestException.class, () -> RequestSizeGuard.check(1_000, 1_000, 96_001));
    }

    @Test
    void theLargestAcceptedStateLeavesRoomForTheLongestQuestionUnderTheGuard() {
        var roomChars = (long) (RequestSizeGuard.STATE_AND_QUESTION_TOKENS * RequestSizeGuard.CHARS_PER_TOKEN)
                - JevSettings.MAX_STATE_CHARS;
        assertTrue(roomChars >= 3_000, "room for the longest question: " + roomChars + " characters");
        RequestSizeGuard.check(JevSettings.MAX_STATE_CHARS, 3_000, 80_000);
    }

    @Test
    void tokensAreEstimatedAtOneAndAHalfCharactersEachRoundedUp() {
        assertEquals(0, RequestSizeGuard.tokens(0));
        assertEquals(1, RequestSizeGuard.tokens(1));
        assertEquals(2, RequestSizeGuard.tokens(3));
        assertEquals(3, RequestSizeGuard.tokens(4));
        assertEquals(64_000, RequestSizeGuard.tokens(96_000));
        assertEquals(64_001, RequestSizeGuard.tokens(96_001));
        assertEquals(1.5, RequestSizeGuard.CHARS_PER_TOKEN);
    }

    @Test
    void aStatePlusQuestionOverTheEstimatedLimitIsNeverSent() {
        var request = longRequest(70_000, Situation.NOW);
        var exception = assertThrows(SystemOneRequestException.class, () -> encoder.encode(request));
        assertEquals("state plus the longest question exceed the estimated 32000-token limit", exception.getMessage());
    }

    @Test
    void aRequestOverTheEstimatedTotalLimitIsNeverSent() {
        var request = longRequest(35_000, Situation.NOW, Situation.IF_TARGET_IN_MELEE, Situation.IF_OWN_HEALTH_CRITICAL);
        var exception = assertThrows(SystemOneRequestException.class, () -> encoder.encode(request));
        assertEquals("request exceeds the estimated 64000-token limit", exception.getMessage());
    }

    @Test
    void theSizeGuardMeasuresTheLongestOptionsOfM15() {
        var fits = JevFixtures.detailed(JevFixtures.worstCaseProfile(1), QuestionDetail.STANDARD);
        var body = JsonParser.parseString(encoder.encode(new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(),
                List.of(question(0, MOB_A, Situation.NOW, "hold_ground", "worst_case_000")), Map.of(MOB_A, fits))))
                .getAsJsonObject();
        var option = body.getAsJsonObject("questions").getAsJsonObject("q0").getAsJsonObject("criteria")
                .getAsJsonObject("worst_case_000").toString();
        assertTrue(option.length() <= QuestionBuilderTest.MAX_FULL_OPTION_CHARS, option.length() + " characters");
        var crowded = JevFixtures.detailed(JevFixtures.worstCaseProfile(60), QuestionDetail.STANDARD);
        var options = crowded.tactics().keySet().stream().map(TacticId::value)
                .filter(id -> id.startsWith("worst_case_")).toArray(String[]::new);
        var request = new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(), List.of(question(0, MOB_A,
                Situation.NOW, options)), Map.of(MOB_A, crowded));
        var exception = assertThrows(SystemOneRequestException.class, () -> encoder.encode(request));
        assertEquals("state plus the longest question exceed the estimated 32000-token limit", exception.getMessage());
    }

    @Test
    void theCompactDetailKeepsEvenTheLongestOptionsOfM15WithinTheirCap() {
        var crowded = JevFixtures.detailed(JevFixtures.worstCaseProfile(60), QuestionDetail.COMPACT);
        var options = crowded.tactics().keySet().stream().map(TacticId::value)
                .filter(id -> id.startsWith("worst_case_")).toArray(String[]::new);
        var body = encoder.body(new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(), List.of(question(0, MOB_A,
                Situation.NOW, options)), Map.of(MOB_A, crowded)));
        var criteria = body.getAsJsonObject("questions").getAsJsonObject("q0").getAsJsonObject("criteria");
        assertEquals(60, criteria.size(), "the note excerpts tell the sixty options apart");
        criteria.entrySet().forEach(entry -> assertTrue(entry.getValue().getAsString().length()
                <= CriterionRenderer.COLLISION_CHARS, entry.getKey()));
    }

    private static DecisionRequest longRequest(int descriptionChars, Situation... situations) {
        var tactics = new LinkedHashMap<>(guardian().tactics());
        var id = JevFixtures.tactic("long_speech");
        tactics.put(id, new Tactic(id, Movement.HOLD, Optional.empty(), Optional.of("w".repeat(descriptionChars)), 50));
        var questions = new ArrayList<QuestionSpec>();
        for (var index = 0; index < situations.length; index++) {
            questions.add(question(index, MOB_A, situations[index], "hold_ground", "long_speech"));
        }
        return new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(), questions,
                Map.of(MOB_A, JevFixtures.detailed(JevFixtures.profile(tactics, "Protect the altar and defeat intruders"),
                        QuestionDetail.STANDARD)));
    }

    private static JsonElement resolve(JsonObject state, String path) {
        assertTrue(path.startsWith("`") && path.endsWith("`"), path);
        JsonElement current = state;
        for (var part : path.substring(1, path.length() - 1).split("\\.")) {
            var bracket = part.indexOf('[');
            var name = bracket < 0 ? part : part.substring(0, bracket);
            assertTrue(current.isJsonObject() && current.getAsJsonObject().has(name), path + " at " + name);
            current = current.getAsJsonObject().get(name);
            if (bracket >= 0) {
                var index = Integer.parseInt(part.substring(bracket + 1, part.length() - 1));
                assertTrue(current.isJsonArray() && index < current.getAsJsonArray().size(), path);
                current = current.getAsJsonArray().get(index);
            }
        }
        return current;
    }

    private static List<String> paths(JsonObject question) {
        var instructions = question.getAsJsonObject("instructions");
        var paths = new ArrayList<String>();
        if (instructions.has("objective")) {
            paths.add(instructions.get("objective").getAsString());
        }
        instructions.getAsJsonArray("judge_by").forEach(path -> paths.add(path.getAsString()));
        return paths;
    }

    @Test
    void everyPathOfAStructuredInstructionResolvesInTheStateSentEvenAfterTrimming() {
        var facts = JevFixtures.threeMobFacts();
        var request = new DecisionRequest(ENCOUNTER, 1, facts, List.of(question(0, MOB_B, Situation.NOW, "hold_ground",
                "press_attack"), question(1, MOB_B, Situation.IF_OWN_HEALTH_CRITICAL, "defend_self", "hold_ground")),
                Map.of(MOB_B, JevFixtures.detailed(guardian(), QuestionDetail.FULL)));
        JsonObject smallest = null;
        String previous = null;
        for (var limit = 3_000; limit > 0; limit -= 10) {
            JsonObject body;
            try {
                body = new RequestEncoder("jev-1.13.0", new SemanticEncoder(limit)).body(request);
            } catch (SystemOneRequestException exception) {
                break;
            }
            if (body.toString().equals(previous)) {
                continue;
            }
            previous = body.toString();
            smallest = body;
            var state = body.getAsJsonObject("state");
            for (var entry : body.getAsJsonObject("questions").entrySet()) {
                var paths = paths(entry.getValue().getAsJsonObject());
                assertTrue(paths.size() >= 3, paths.toString());
                paths.forEach(path -> resolve(state, path));
                assertEquals("mob_2", resolve(state, paths.getFirst().replace("objective", "ref")).getAsString());
            }
        }
        var mobs = smallest.getAsJsonObject("state").getAsJsonArray("mobs");
        assertEquals(1, mobs.size(), "the mobs without questions were trimmed before the asked one");
        assertEquals("`mobs[0].objective`", smallest.getAsJsonObject("questions").getAsJsonObject("q0")
                .getAsJsonObject("instructions").get("objective").getAsString());
    }

    @Test
    void eachSituationHasItsInstructionAtEachDetail() {
        var request = new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(), List.of(
                question(0, MOB_A, Situation.NOW, "press_attack", "keep_range"),
                question(1, MOB_A, Situation.IF_TARGET_IN_MELEE, "press_attack", "hold_ground"),
                question(2, MOB_A, Situation.IF_OWN_HEALTH_CRITICAL, "defend_self", "hold_ground")),
                Map.of(MOB_A, JevFixtures.detailed(guardian(), QuestionDetail.COMPACT)));
        var compact = encoder.body(request).getAsJsonObject("questions");
        assertEquals("Which tactic best serves mob_1's objective now?", compact.getAsJsonObject("q0").get("instructions")
                .getAsString());
        assertEquals("If mob_1's target comes inside melee reach soon, which tactic then?", compact.getAsJsonObject("q1")
                .get("instructions").getAsString());
        assertEquals("If mob_1's health becomes critical soon, which tactic then?", compact.getAsJsonObject("q2")
                .get("instructions").getAsString());
        for (var detail : List.of(QuestionDetail.STANDARD, QuestionDetail.FULL)) {
            var structured = encoder.body(new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(), request.questions(),
                    Map.of(MOB_A, JevFixtures.detailed(guardian(), detail)))).getAsJsonObject("questions");
            var now = structured.getAsJsonObject("q0").getAsJsonObject("instructions");
            var melee = structured.getAsJsonObject("q1").getAsJsonObject("instructions");
            var critical = structured.getAsJsonObject("q2").getAsJsonObject("instructions");
            assertEquals("Which tactic should mob_1 use now against its current target?", now.get("question")
                    .getAsString());
            assertFalse(now.has("assume"));
            assertEquals("Suppose the target of mob_1 comes inside melee reach in the next seconds. Which tactic should"
                    + " mob_1 switch to then?", melee.get("question").getAsString());
            assertEquals("the target is inside melee reach", melee.get("assume").getAsString());
            assertEquals("mob_1's health is critical", critical.get("assume").getAsString());
            assertEquals(detail == QuestionDetail.FULL, now.has("focus"), detail.name());
            assertEquals(List.of("question", "objective", "judge_by"), List.copyOf(now.keySet()).subList(0, 3));
        }
    }

    @Test
    void fitChecksAreNoulQuestionsAfterTheChoicesAndWithoutThemTheBodyIsTheSame() {
        var full = JevFixtures.detailed(guardian(), QuestionDetail.FULL);
        var questions = List.of(question(0, MOB_A, Situation.NOW, "press_attack", "keep_range"),
                question(1, MOB_A, Situation.IF_OWN_HEALTH_CRITICAL, "defend_self", "hold_ground"));
        var plain = encoder.body(new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(), questions,
                Map.of(MOB_A, full)));
        assertFalse(plain.toString().contains("noul"));
        var checks = List.of(new FitCheck(QuestionKey.of(2), MOB_A, tactic("press_attack")),
                new FitCheck(QuestionKey.of(3), MOB_A, tactic("keep_range")));
        var fitted = encoder.body(new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(), questions,
                Map.of(MOB_A, JevFixtures.detailed(guardian(), QuestionDetail.FULL, true)), checks));
        var keys = List.copyOf(fitted.getAsJsonObject("questions").keySet());
        assertEquals(List.of("q0", "q1", "q2", "q3"), keys);
        var noul = fitted.getAsJsonObject("questions").getAsJsonObject("q2");
        assertEquals("noul", noul.get("type").getAsString());
        assertEquals("Is `press_attack` a sound tactic for mob_1 right now?", noul.getAsJsonObject("instructions")
                .get("question").getAsString());
        assertEquals(List.of("movement", "skill"), List.copyOf(noul.getAsJsonObject("instructions")
                .getAsJsonObject("tactic").keySet()));
        assertEquals(List.of("true", "false"), List.copyOf(noul.getAsJsonObject("criteria").keySet()));
        paths(noul).forEach(path -> resolve(fitted.getAsJsonObject("state"), path));
        fitted.getAsJsonObject("questions").remove("q2");
        fitted.getAsJsonObject("questions").remove("q3");
        assertEquals(plain, fitted, "fit checks add only their noul questions");
    }

    @Test
    void aNowLeftWithOneDistinctOptionDropsItsContingenciesAndItsFitChecks() {
        var twins = new LinkedHashMap<>(guardian().tactics());
        twins.put(tactic("hold_twin"), new Tactic(tactic("hold_twin"), Movement.HOLD, Optional.empty(), Optional.empty(),
                5));
        var profile = JevFixtures.detailed(JevFixtures.profile(twins, "Protect the altar and defeat intruders"),
                QuestionDetail.FULL, true);
        var facts = JevFixtures.threeMobFacts();
        var request = new DecisionRequest(ENCOUNTER, 1, facts, List.of(
                question(0, MOB_A, Situation.NOW, "hold_ground", "hold_twin"),
                question(1, MOB_A, Situation.IF_OWN_HEALTH_CRITICAL, "defend_self", "hold_ground"),
                question(2, MOB_B, Situation.NOW, "press_attack", "keep_range")), Map.of(MOB_A, profile, MOB_B, profile),
                List.of(new FitCheck(QuestionKey.of(3), MOB_A, tactic("hold_ground")),
                        new FitCheck(QuestionKey.of(4), MOB_B, tactic("keep_range"))));
        var prepared = assertInstanceOf(Preparation.Ready.class, encoder.prepare(request)).prepared();
        assertEquals(Set.of(QuestionKey.of(0), QuestionKey.of(1)), prepared.droppedQuestions());
        assertEquals(Set.of(MOB_A), prepared.localMobs());
        assertEquals(List.of(QuestionKey.of(2)), prepared.sent().questions().stream().map(QuestionSpec::key).toList());
        assertEquals(List.of(QuestionKey.of(4)), prepared.sent().fitChecks().stream().map(FitCheck::key).toList());
        assertEquals(prepared.body().length(), prepared.bodyChars());
        var onlyTwins = new DecisionRequest(ENCOUNTER, 1, facts, List.of(question(0, MOB_A, Situation.NOW,
                "hold_ground", "hold_twin")), Map.of(MOB_A, profile));
        assertEquals(Set.of(MOB_A), assertInstanceOf(Preparation.NothingToAsk.class, encoder.prepare(onlyTwins))
                .localMobs());
    }

    @Test
    void anInvalidRequestIsPreparedAsInvalidAndTheGuardStaysAtOneAndAHalfWhateverTheBudgetLearns() {
        var tooBig = longRequest(70_000, Situation.NOW);
        assertEquals("state plus the longest question exceed the estimated 32000-token limit",
                assertInstanceOf(Preparation.Invalid.class, encoder.prepare(tooBig)).detail());
        var budget = new RequestBudget(new EngineSettings(600, 300, 8, 4, 10_000_000, 10, 2, 64, 16, 64, 2000,
                800_000_000L));
        for (var index = 1; index <= 30; index++) {
            assertTrue(budget.tryReserve(new UUID(0, 1), new EncounterId(index), 10, index * 1_000_000_000L));
            budget.complete(new InFlightRequest(new EncounterId(index), new UUID(0, 1), 1, 1, Map.of(QuestionKey.of(0),
                    question(0, MOB_A, Situation.NOW, "hold_ground", "press_attack")), Map.of(), 1, 0, 0, 1, 10, 4000),
                    OptionalInt.of(1000), index * 1_000_000_000L);
        }
        assertEquals(RequestBudget.MAX_CHARS_PER_TOKEN, budget.charsPerToken());
        assertEquals(1.5, RequestSizeGuard.CHARS_PER_TOKEN);
        assertThrows(SystemOneRequestException.class, () -> RequestSizeGuard.check(1_000, 1_000, 96_001));
    }

    @Test
    void theProductionRequestOfTheExampleCostsAtMostFourteenHundredTokensAtOneAndAHalfInCompact() {
        var example = ScenarioFacts.example();
        var compact = encoder.encode(ScenarioFacts.production(JevFixtures.detailed(example, QuestionDetail.COMPACT)));
        assertTrue(RequestSizeGuard.tokens(compact.length()) <= 1_400, compact.length() + " characters");
        var standard = encoder.encode(ScenarioFacts.production(JevFixtures.detailed(example, QuestionDetail.STANDARD)));
        var full = encoder.encode(ScenarioFacts.production(JevFixtures.detailed(example, QuestionDetail.FULL)));
        assertTrue(compact.length() < standard.length() && standard.length() < full.length());
        var fitted = ScenarioFacts.production(JevFixtures.detailed(example, QuestionDetail.FULL, true));
        var checks = new ArrayList<FitCheck>();
        fitted.questions().getFirst().options().forEach(option -> checks.add(new FitCheck(QuestionKey.of(2
                + checks.size()), fitted.questions().getFirst().mob(), option)));
        var withChecks = encoder.encode(new DecisionRequest(fitted.encounter(), 1, fitted.facts(), fitted.questions(),
                fitted.profiles(), checks));
        assertTrue(withChecks.length() > full.length(), "the budget reserves more with fit checks");
    }
}
