package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures and golden files against the public TypeSafe OpenAPI 0.2.0: required properties, no undeclared ones,
 * and value types. Tolerance for undeclared fields is tested separately in the parser tests.
 */
class ContractFixturesTest {
    @Test
    void theSuccessResponseIsASystemOneResponseWithChoiceAnswersAndUsage() {
        var root = object("response-valid.json");
        assertEquals(Set.of("model", "answers", "usage"), root.keySet());
        assertTrue(root.get("model").getAsJsonPrimitive().isString());
        assertEquals(Set.of("input_tokens", "output_tokens"), root.getAsJsonObject("usage").keySet());
        for (var entry : root.getAsJsonObject("answers").entrySet()) {
            var answer = entry.getValue().getAsJsonObject();
            assertEquals(Set.of("type", "choice", "confidence", "probabilities"), answer.keySet(), entry.getKey());
            assertEquals("choice", answer.get("type").getAsString());
            answer.getAsJsonObject("probabilities").entrySet()
                    .forEach(probability -> assertTrue(probability.getValue().getAsJsonPrimitive().isNumber()));
        }
    }

    @Test
    void validationErrorsAreHttpValidationErrorsWithStringOrIntegerLocations() {
        for (var name : new String[] {"validation-model.json", "validation-other.json"}) {
            var root = object(name);
            assertEquals(Set.of("detail"), root.keySet(), name);
            for (JsonElement error : root.getAsJsonArray("detail")) {
                var keys = error.getAsJsonObject().keySet();
                assertTrue(keys.containsAll(Set.of("loc", "msg", "type")), name);
                assertTrue(Set.of("loc", "msg", "type", "input", "ctx").containsAll(keys), name);
                error.getAsJsonObject().getAsJsonArray("loc").forEach(part -> assertTrue(part.getAsJsonPrimitive().isString()
                        || part.getAsJsonPrimitive().isNumber()));
            }
        }
    }

    @Test
    void theGoldenRequestIsASystemOneRequestWithChoiceQuestions() {
        var root = object("golden/request-two-questions.json");
        assertEquals(Set.of("model", "state", "questions"), root.keySet());
        assertTrue(root.get("state").isJsonObject());
        for (var entry : root.getAsJsonObject("questions").entrySet()) {
            var question = entry.getValue().getAsJsonObject();
            assertTrue(Set.of("type", "instructions", "criteria").containsAll(question.keySet()), entry.getKey());
            assertTrue(question.keySet().containsAll(Set.of("type", "criteria")), entry.getKey());
            assertEquals("choice", question.get("type").getAsString());
            question.getAsJsonObject("criteria").entrySet()
                    .forEach(criterion -> assertTrue(criterion.getValue().getAsJsonPrimitive().isString()));
        }
    }

    private static JsonObject object(String name) {
        return JsonParser.parseString(SystemOneContractServer.fixture(name)).getAsJsonObject();
    }

    @Test
    void structuredCriteriaAndInstructionsAndFitChecksKeepTheShapeOfTheOpenApi() {
        var encoder = new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000));
        var profile = JevFixtures.detailed(JevFixtures.guardian(), QuestionDetail.FULL, true);
        var request = JevFixtures.request();
        var checks = List.of(new FitCheck(QuestionKey.of(2),
                JevFixtures.MOB_A, JevFixtures.tactic("press_attack")));
        var body = encoder.body(new DecisionRequest(request.encounter(), 4, request.facts(),
                request.questions(), Map.of(JevFixtures.MOB_A, profile), checks));
        assertEquals(Set.of("model", "state", "questions"), body.keySet());
        for (var entry : body.getAsJsonObject("questions").entrySet()) {
            var question = entry.getValue().getAsJsonObject();
            assertEquals(Set.of("type", "instructions", "criteria"), question.keySet(), entry.getKey());
            assertTrue(question.get("instructions").isJsonObject(), entry.getKey());
            if (question.get("type").getAsString().equals("noul")) {
                assertEquals(Set.of("true", "false"), question.getAsJsonObject("criteria").keySet());
                continue;
            }
            assertEquals("choice", question.get("type").getAsString());
            question.getAsJsonObject("criteria").entrySet().forEach(criterion -> assertTrue(criterion.getValue()
                    .isJsonObject(), "ChoiceQuestion.criteria admits an object"));
        }
    }

    @Test
    void theResponseWithFitsHasOneNoulAnswerPerFitCheck() {
        var root = object("response-with-fits.json");
        assertEquals(Set.of("model", "answers", "usage"), root.keySet());
        var nouls = root.getAsJsonObject("answers").entrySet().stream().filter(entry -> entry.getValue()
                .getAsJsonObject().get("type").getAsString().equals("noul")).toList();
        assertEquals(3, nouls.size());
        nouls.forEach(entry -> assertEquals(Set.of("type", "noul"), entry.getValue().getAsJsonObject().keySet()));
    }
}
