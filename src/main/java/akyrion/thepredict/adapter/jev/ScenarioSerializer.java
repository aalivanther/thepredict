package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.QuestionSpec;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * JSON document of one recorded scenario (M13.6), format {@value #FORMAT}. It holds the exact request body, which the
 * encoder already wrote with aliases only (rule I6); the question keys, situations and options; the counts of mobs,
 * participants and body characters for the token calibration; the answer; and one label per question with an empty
 * list of acceptable tactics and an empty forbidden tactic, which a person fills in by hand. Mob and player identities
 * are never written. Pure and thread-safe.
 */
final class ScenarioSerializer {
    /** Version of the document layout. */
    static final int FORMAT = 1;

    private ScenarioSerializer() {
    }

    static String document(String body, DecisionRequest request, DecisionResult.Answered answer) {
        var document = new JsonObject();
        document.addProperty("format", FORMAT);
        document.addProperty("requestChars", body.length());
        document.addProperty("mobs", request.facts().mobs().size());
        document.addProperty("participants", request.facts().participants().size());
        document.add("request", StrictJson.parseObject(body, "request body"));
        var questions = new JsonArray();
        var labels = new JsonObject();
        for (var question : request.questions()) {
            questions.add(question(question));
            labels.add(question.key().value(), emptyLabel());
        }
        document.add("questions", questions);
        document.add("answer", answer(answer));
        document.add("labels", labels);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document);
    }

    private static JsonObject question(QuestionSpec spec) {
        var question = new JsonObject();
        question.addProperty("key", spec.key().value());
        question.addProperty("situation", spec.situation().name());
        var options = new JsonArray();
        spec.options().forEach(option -> options.add(option.value()));
        question.add("options", options);
        return question;
    }

    private static JsonObject answer(DecisionResult.Answered answered) {
        var answer = new JsonObject();
        answer.addProperty("model", answered.effectiveModel());
        answer.addProperty("inputTokens", answered.inputTokens());
        var byKey = new JsonObject();
        answered.answers().forEach((key, value) -> {
            var one = new JsonObject();
            one.addProperty("confidence", value.confidence());
            var probabilities = new JsonObject();
            value.probabilities().forEach((tactic, probability) -> probabilities.addProperty(tactic.value(),
                    probability));
            one.add("probabilities", probabilities);
            byKey.add(key.value(), one);
        });
        answer.add("answers", byKey);
        return answer;
    }

    private static JsonObject emptyLabel() {
        var label = new JsonObject();
        label.add("acceptable", new JsonArray());
        label.addProperty("forbidden", "");
        return label;
    }
}
