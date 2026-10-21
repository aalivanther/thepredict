package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_A;
import static akyrion.thepredict.adapter.jev.JevFixtures.question;
import static akyrion.thepredict.adapter.jev.JevFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseParserTest {
    private static final List<QuestionSpec> TWO = JevFixtures.request().questions();
    private static final List<QuestionSpec> ONE = List.of(question(0, MOB_A, Situation.NOW, "keep_range", "hold_ground"));

    @Test
    void aValidResponseGivesAnswersModelAndInputTokens() {
        var answered = ResponseParser.parse(SystemOneContractServer.fixture("response-valid.json"), TWO);
        assertEquals("jev-1.13.0", answered.effectiveModel());
        assertEquals(412, answered.inputTokens());
        var first = answered.answers().get(QuestionKey.of(0));
        assertEquals(0.71, first.confidence());
        assertEquals(0.7, first.probabilityOf(tactic("keep_range")));
        assertEquals(tactic("keep_range"), first.best());
        assertEquals(tactic("defend_self"), answered.answers().get(QuestionKey.of(1)).best());
    }

    @Test
    void anUndocumentedFieldIsTolerated() {
        var answered = ResponseParser.parse(response("\"keep_range\"", "0.8", "0.6", "0.4",
                ",\"reasoning_trace\":{\"steps\":3}").replace("\"usage\":", "\"latency_ms\":97,\"usage\":"), ONE);
        assertEquals(0.8, answered.answers().get(QuestionKey.of(0)).confidence());
    }

    @Test
    void aMissingQuestionIsInvalid() {
        var body = JsonParser.parseString(SystemOneContractServer.fixture("response-valid.json")).getAsJsonObject();
        body.getAsJsonObject("answers").remove("q1");
        assertRejected("answers.q1 must be an object", body.toString(), TWO);
    }

    @Test
    void anExtraQuestionIsInvalidWithoutNamingIt() {
        var body = response("\"keep_range\"", "0.8", "0.6", "0.4", "")
                .replace("\"answers\":{", "\"answers\":{\"zz_marker\":{},");
        assertRejected("answers contains a question key that was not sent", body, ONE);
    }

    @Test
    void anotherQuestionTypeIsInvalid() {
        assertRejected("answers.q0.type must be choice",
                response("\"keep_range\"", "0.8", "0.6", "0.4", "").replace("\"choice\",\"choice\"", "\"score\",\"choice\""),
                ONE);
    }

    @Test
    void aForeignOptionOrAMissingOptionIsInvalid() {
        var foreign = response("\"keep_range\"", "0.8", "0.6", "0.4", "").replace("\"hold_ground\"", "\"zz_marker\"");
        assertRejected("answers.q0.probabilities must name exactly the options sent", foreign, ONE);
        var missing = "{\"model\":\"jev-1.13.0\",\"answers\":{\"q0\":{\"type\":\"choice\",\"choice\":\"keep_range\","
                + "\"confidence\":0.8,\"probabilities\":{\"keep_range\":1.0}}},\"usage\":{\"input_tokens\":5,\"output_tokens\":1}}";
        assertRejected("answers.q0.probabilities must name exactly the options sent", missing, ONE);
    }

    @Test
    void probabilitiesMustBeFiniteAndWithinTheUnitInterval() {
        assertRejected("answers.q0.probabilities must be finite and within [0, 1]",
                response("\"keep_range\"", "0.8", "1.1", "-0.1", ""), ONE);
        assertRejected("answers.q0.probabilities must be finite and within [0, 1]",
                response("\"keep_range\"", "0.8", "1e400", "0", ""), ONE);
        assertRejected("response is not valid JSON", response("\"keep_range\"", "0.8", "NaN", "0.5", ""), ONE);
    }

    @Test
    void theSumIsAcceptedWithinTwoHundredthsAndRejectedBeyond() {
        ResponseParser.parse(response("\"keep_range\"", "0.8", "0.49", "0.49", ""), ONE);
        ResponseParser.parse(response("\"keep_range\"", "0.8", "0.52", "0.5", ""), ONE);
        assertRejected("answers.q0.probabilities must sum to 1 within 0.02",
                response("\"keep_range\"", "0.8", "0.49", "0.489", ""), ONE);
        assertRejected("answers.q0.probabilities must sum to 1 within 0.02",
                response("\"keep_range\"", "0.8", "0.52", "0.501", ""), ONE);
        assertRejected("answers.q0.probabilities must sum to 1 within 0.02",
                response("\"keep_range\"", "0.8", "0.5", "0.45", ""), ONE);
    }

    @Test
    void confidenceMustBePresentFiniteAndWithinTheUnitInterval() {
        assertRejected("answers.q0.confidence must be finite and within [0, 1]",
                response("\"keep_range\"", "1.2", "0.6", "0.4", ""), ONE);
        assertRejected("answers.q0.confidence must be a number",
                response("\"keep_range\"", "\"high\"", "0.6", "0.4", ""), ONE);
        assertRejected("answers.q0.confidence must be a number",
                response("\"keep_range\"", "0.8", "0.6", "0.4", "").replace("\"confidence\":0.8,", ""), ONE);
        ResponseParser.parse(response("\"keep_range\"", "0", "0.6", "0.4", ""), ONE);
        ResponseParser.parse(response("\"keep_range\"", "1", "0.6", "0.4", ""), ONE);
    }

    @Test
    void theChoiceMustBeAnOptionWithTheHighestProbabilityAndATieAcceptsEither() {
        assertRejected("answers.q0.choice is not the most probable option",
                response("\"hold_ground\"", "0.8", "0.6", "0.4", ""), ONE);
        assertRejected("answers.q0.choice is not an option sent", response("\"zz_marker\"", "0.8", "0.6", "0.4", ""), ONE);
        ResponseParser.parse(response("\"keep_range\"", "0.5", "0.5", "0.5", ""), ONE);
        ResponseParser.parse(response("\"hold_ground\"", "0.5", "0.5", "0.5", ""), ONE);
    }

    @Test
    void theRootNeedsModelAnswersAndNonNegativeIntegerInputTokens() {
        var valid = response("\"keep_range\"", "0.8", "0.6", "0.4", "");
        assertRejected("model must not be blank", valid.replace("\"jev-1.13.0\"", "\" \""), ONE);
        assertRejected("model must be a string", valid.replace("\"model\":\"jev-1.13.0\",", ""), ONE);
        assertRejected("usage.input_tokens must not be negative", valid.replace("\"input_tokens\":5", "\"input_tokens\":-1"), ONE);
        assertRejected("usage.input_tokens must be an integer", valid.replace("\"input_tokens\":5", "\"input_tokens\":5.5"), ONE);
        assertRejected("usage must be an object", valid.replace("\"usage\":{\"input_tokens\":5,\"output_tokens\":1}", "\"usage\":5"), ONE);
        assertRejected("answers must be an object", valid.replace("\"answers\":{", "\"answers\":[{").replace("}}},", "}}}],"), ONE);
        assertEquals(0, ResponseParser.parse(valid.replace("\"input_tokens\":5", "\"input_tokens\":0"), ONE).inputTokens());
    }

    @Test
    void brokenJsonAndTrailingContentAreInvalid() {
        assertRejected("response is not valid JSON", "{\"model\":", ONE);
        assertRejected("response is not valid JSON", response("\"keep_range\"", "0.8", "0.6", "0.4", "") + " {}", ONE);
        assertRejected("response must be an object", "[]", ONE);
    }

    @Test
    void repeatedKeysAndDeepNestingAreInvalid() {
        assertRejected("response repeats a key", response("\"keep_range\"", "0.8", "0.6", "0.4", "")
                .replace("\"hold_ground\":0.4", "\"hold_ground\":0.4,\"hold_ground\":0.4"), ONE);
        assertRejected("response repeats a key", "{\"model\":\"a\",\"model\":\"b\"}", ONE);
        assertRejected("response is nested too deeply", "[".repeat(80) + "]".repeat(80), ONE);
    }

    @Test
    void theSpecRequiresOutputTokensButItsAbsenceIsTolerated() {
        var withOutput = response("\"keep_range\"", "0.8", "0.6", "0.4", "");
        assertEquals(5, ResponseParser.parse(withOutput, ONE).inputTokens());
        var withoutOutput = withOutput.replace(",\"output_tokens\":1", "");
        assertEquals(5, ResponseParser.parse(withoutOutput, ONE).inputTokens());
    }

    @Test
    void noErrorQuotesTheBody() {
        var bodies = List.of(response("\"zz_marker\"", "0.8", "0.6", "0.4", ""),
                response("\"keep_range\"", "0.8", "0.6", "0.4", "").replace("\"hold_ground\"", "\"zz_marker\""),
                "{\"zz_marker\":", "{\"model\":\"zz_marker\",\"answers\":{\"zz_marker\":1},\"usage\":{\"input_tokens\":1}}");
        for (var body : bodies) {
            var exception = assertThrows(SystemOneProtocolException.class, () -> ResponseParser.parse(body, ONE));
            assertFalse(exception.getMessage().contains("zz_marker"), exception.getMessage());
        }
    }

    private static String response(String choice, String confidence, String keepRange, String holdGround, String extra) {
        return "{\"model\":\"jev-1.13.0\",\"answers\":{\"q0\":{\"type\":\"choice\",\"choice\":" + choice
                + ",\"confidence\":" + confidence + ",\"probabilities\":{\"keep_range\":" + keepRange
                + ",\"hold_ground\":" + holdGround + "}" + extra + "}},\"usage\":{\"input_tokens\":5,\"output_tokens\":1}}";
    }

    private static void assertRejected(String rule, String body, List<QuestionSpec> questions) {
        var exception = assertThrows(SystemOneProtocolException.class, () -> ResponseParser.parse(body, questions));
        assertEquals(rule, exception.getMessage());
    }

    private static final String FIT_BODY = "{\"model\":\"jev-1.13.0\",\"answers\":{\"q0\":{\"type\":\"choice\","
            + "\"choice\":\"keep_range\",\"confidence\":0.6,\"probabilities\":{\"keep_range\":0.7,\"hold_ground\":0.3}},"
            + "\"q1\":%s,\"q2\":{\"type\":\"noul\",\"noul\":0.1}},\"usage\":{\"input_tokens\":9,\"output_tokens\":1}}";

    private static final List<FitCheck> CHECKS = List.of(new FitCheck(QuestionKey.of(1), MOB_A, tactic("keep_range")),
            new FitCheck(QuestionKey.of(2), MOB_A, tactic("hold_ground")));

    @Test
    void aFitCheckIsANoulWithAFiniteProbabilityWithinTheUnitInterval() {
        var answered = ResponseParser.parse(String.format(FIT_BODY, "{\"type\":\"noul\",\"noul\":0.83}"), ONE,
                CHECKS);
        assertEquals(0.83, answered.fits().get(QuestionKey.of(1)));
        assertEquals(0.1, answered.fits().get(QuestionKey.of(2)));
        assertEquals(1, answered.answers().size());
        var missing = assertThrows(SystemOneProtocolException.class, () -> ResponseParser.parse(
                String.format(FIT_BODY, "{\"type\":\"noul\",\"noul\":0.83}").replace(",\"q2\":{\"type\":\"noul\","
                        + "\"noul\":0.1}", ""), ONE, CHECKS));
        assertEquals("answers.q2 must be an object", missing.getMessage());
        var choice = assertThrows(SystemOneProtocolException.class, () -> ResponseParser.parse(String.format(FIT_BODY,
                "{\"type\":\"choice\",\"noul\":0.5}"), ONE, CHECKS));
        assertEquals("answers.q1.type must be noul", choice.getMessage());
        var range = assertThrows(SystemOneProtocolException.class, () -> ResponseParser.parse(String.format(FIT_BODY,
                "{\"type\":\"noul\",\"noul\":1.5}"), ONE, CHECKS));
        assertEquals("answers.q1.noul must be finite and within [0, 1]", range.getMessage());
        assertThrows(SystemOneProtocolException.class, () -> ResponseParser.parse(String.format(FIT_BODY,
                "{\"type\":\"noul\",\"noul\":0.5}"), ONE), "a fit that was not sent is a key that was not sent");
    }

    private static final GroupBrief PLAN = new GroupBrief(GroupQuestions.PLAN_AND_LEADER,
            List.of(GroupPlan.PRESS_MAIN_TARGET.option(), GroupPlan.SURROUND_TARGET.option()), 7L, true);

    private static String withG0(String g0) {
        return response("\"keep_range\"", "0.8", "0.6", "0.4", "").replace("\"answers\":{", "\"answers\":{" + g0);
    }

    @Test
    void aValidG0CarriesTheEpochOfTheBriefNotOneReadFromTheText() {
        var answered = ResponseParser.parse(withG0("\"g0\":{\"type\":\"choice\",\"choice\":\"surround_target\","
                + "\"confidence\":0.7,\"probabilities\":{\"press_main_target\":0.3,\"surround_target\":0.7}},"), ONE,
                List.of(), PLAN);
        assertEquals(GroupPlan.SURROUND_TARGET, answered.group().orElseThrow().plan());
        assertEquals(7L, answered.group().orElseThrow().membershipEpoch());
        assertEquals(0.8, answered.answers().get(QuestionKey.of(0)).confidence());
    }

    @Test
    void aMissingOrBrokenG0IsDroppedAloneAndTheTacticAnswersStay() {
        var broken = List.of("",
                "\"g0\":{\"type\":\"choice\",\"choice\":\"press_main_target\",\"confidence\":0.7,"
                        + "\"probabilities\":{\"press_main_target\":0.3,\"surround_target\":0.7}},",
                "\"g0\":{\"type\":\"choice\",\"choice\":\"split_targets\",\"confidence\":0.7,"
                        + "\"probabilities\":{\"split_targets\":0.6,\"surround_target\":0.4}},",
                "\"g0\":{\"type\":\"choice\",\"choice\":\"surround_target\",\"confidence\":1.7,"
                        + "\"probabilities\":{\"press_main_target\":0.3,\"surround_target\":0.7}},",
                "\"g0\":{\"type\":\"choice\",\"choice\":\"surround_target\",\"confidence\":0.7,"
                        + "\"probabilities\":{\"press_main_target\":0.5,\"surround_target\":0.7}},",
                "\"g0\":{\"type\":\"noul\",\"noul\":0.5},");
        for (var g0 : broken) {
            var answered = ResponseParser.parse(withG0(g0), ONE, List.of(), PLAN);
            assertTrue(answered.group().isEmpty(), g0);
            assertEquals(tactic("keep_range"), answered.answers().get(QuestionKey.of(0)).best(), g0);
        }
    }

    @Test
    void aPlanOnlyAnswerCarriesTheGroupPlanAndNoTacticAnswer() {
        var planOnly = new GroupBrief(GroupQuestions.PLAN_ONLY, PLAN.candidates(), 3L, true);
        var answered = ResponseParser.parse("{\"model\":\"jev-1.13.0\",\"answers\":{\"g0\":{\"type\":\"choice\","
                + "\"choice\":\"press_main_target\",\"confidence\":0.9,\"probabilities\":{\"press_main_target\":0.9,"
                + "\"surround_target\":0.1}}},\"usage\":{\"input_tokens\":10}}", List.of(), List.of(), planOnly);
        assertTrue(answered.answers().isEmpty());
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, answered.group().orElseThrow().plan());
        assertEquals(3L, answered.group().orElseThrow().membershipEpoch());
    }

    @Test
    void aG0ThatWasNotAskedIsAKeyThatWasNotSent() {
        assertRejected("answers contains a question key that was not sent", withG0("\"g0\":{\"type\":\"choice\","
                + "\"choice\":\"surround_target\",\"confidence\":0.7,\"probabilities\":{\"press_main_target\":0.3,"
                + "\"surround_target\":0.7}},"), ONE);
    }

    @Test
    void anAnswerNamingAnOptionThatWasMergedIsInvalid() {
        var merged = List.of(question(0, MOB_A, Situation.NOW, "keep_range", "defend_self"));
        assertRejected("answers.q0.probabilities must name exactly the options sent", response("\"keep_range\"", "0.6",
                "0.7", "0.3", ""), merged);
    }
}
