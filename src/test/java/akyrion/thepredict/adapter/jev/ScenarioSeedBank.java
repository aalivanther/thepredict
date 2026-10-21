package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.TacticId;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seed bank of M13.6: one scenario per situation of M13.5, built with the example profile and the real encoder, in the
 * layout of {@link ScenarioRecorder} without an answer, and with proposed labels that the user reviews before any
 * calibration. {@code ScenarioSeedBankTest} keeps the files in {@code src/test/resources/scenarios/seed} equal to what
 * this class builds, so an encoder change shows up as a failing test. The seeds keep the {@code compact} detail they
 * were written with, pinned since {@code full} became the default of M16.10.
 */
final class ScenarioSeedBank {
    private ScenarioSeedBank() {
    }

    /** File name of a seed. */
    static String fileName(ScenarioFacts.Seed seed) {
        return "seed-" + seed.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".json";
    }

    /** The document of one seed. */
    static String document(ScenarioFacts.Seed seed) {
        var request = ScenarioFacts.seed(seed, JevFixtures.detailed(ScenarioFacts.example(), QuestionDetail.COMPACT));
        var body = new RequestEncoder(LiveJevGate.MODEL, new SemanticEncoder(12_000)).encode(request);
        var placeholder = new LinkedHashMap<QuestionKey, Answer>();
        request.questions().forEach(question -> placeholder.put(question.key(), uniform(question.options())));
        var document = JsonParser.parseString(ScenarioSerializer.document(body, request,
                new DecisionResult.Answered(placeholder, LiveJevGate.MODEL, 0))).getAsJsonObject();
        document.remove("answer");
        document.addProperty("seed", seed.name());
        document.addProperty("labelsProposedBy", "M13 seed bank; review before calibrating");
        var label = new JsonObject();
        var acceptable = new JsonArray();
        seed.acceptable().forEach(tactic -> acceptable.add(tactic.value()));
        label.add("acceptable", acceptable);
        label.addProperty("forbidden", seed.forbidden());
        var labels = new JsonObject();
        labels.add("q0", label);
        document.add("labels", labels);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document) + "\n";
    }

    private static Answer uniform(List<TacticId> options) {
        var probabilities = new LinkedHashMap<TacticId, Double>();
        options.forEach(option -> probabilities.put(option, 1.0 / options.size()));
        return new Answer(Map.copyOf(probabilities), 0.0);
    }
}
