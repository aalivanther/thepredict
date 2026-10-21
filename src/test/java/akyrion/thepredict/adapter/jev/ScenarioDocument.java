package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One scenario of the bank of M13.6 or of the labelled bank of M16.9, read back from the file that
 * {@link ScenarioRecorder}, the seed bank or {@link ScenarioBank} wrote: the exact body to replay, its questions and the
 * hand labels. Reads format 1 (a list of acceptable tactics and one forbidden tactic as text) and format 2 (best,
 * acceptable and forbidden lists, rationale, review status and the {@code uninformative} mark). A question without
 * acceptable tactics is unlabelled and only counts for latency and tokens; an uninformative one is labelled but never
 * counts for accuracy.
 *
 * @param name file name without extension
 * @param body the request body, with the model to replay
 * @param questions questions of the body with their options
 * @param labels label of each labelled question
 * @param requestChars characters of the recorded body
 * @param participants participants of the encounter
 */
record ScenarioDocument(String name, JsonObject body, List<QuestionSpec> questions, Map<QuestionKey, Label> labels,
                        int requestChars, int participants) {
    private static final MobKey MOB = new MobKey(new UUID(0xBE7CL, 1), 0);

    /**
     * Hand label of one question.
     *
     * @param best the tactics a person thinks best
     * @param acceptable the tactics a person accepts
     * @param forbidden the tactics that are a critical failure
     * @param status {@code PROPOSED}, {@code REVIEWED} or {@code DISPUTED}
     * @param uninformative whether every option is acceptable, so the label says nothing about accuracy
     */
    record Label(Set<TacticId> best, Set<TacticId> acceptable, Set<TacticId> forbidden, String status,
                 boolean uninformative) {
        /** Whether the user reviewed the label. */
        boolean reviewed() {
            return "REVIEWED".equals(status);
        }
    }

    /** Reads every {@code .json} file of a folder, sorted by name. */
    static List<ScenarioDocument> readAll(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            var documents = new ArrayList<ScenarioDocument>();
            for (var file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                documents.add(read(file.getFileName().toString().replace(".json", ""), Files.readString(file)));
            }
            return List.copyOf(documents);
        }
    }

    /** Parses one document. */
    static ScenarioDocument read(String name, String text) {
        var document = JsonParser.parseString(text).getAsJsonObject();
        var questions = new ArrayList<QuestionSpec>();
        for (var element : document.getAsJsonArray("questions")) {
            var question = element.getAsJsonObject();
            var options = new ArrayList<TacticId>();
            question.getAsJsonArray("options").forEach(option -> options.add(new TacticId(option.getAsString())));
            questions.add(new QuestionSpec(new QuestionKey(question.get("key").getAsString()), MOB,
                    Situation.valueOf(question.get("situation").getAsString()), options));
        }
        var labels = new LinkedHashMap<QuestionKey, Label>();
        var labelled = document.has("labels") ? document.getAsJsonObject("labels") : new JsonObject();
        for (var entry : labelled.entrySet()) {
            var key = new QuestionKey(entry.getKey());
            var options = questions.stream().filter(question -> question.key().equals(key)).findFirst()
                    .map(QuestionSpec::options).orElse(List.of());
            var label = label(entry.getValue().getAsJsonObject(), options);
            if (!label.acceptable().isEmpty()) {
                labels.put(key, label);
            }
        }
        return new ScenarioDocument(name, document.getAsJsonObject("request"), List.copyOf(questions),
                Map.copyOf(labels), document.get("requestChars").getAsInt(), document.get("participants").getAsInt());
    }

    /** Reads one label of format 1 or 2 for the options of its question. */
    static Label label(JsonObject label, List<TacticId> options) {
        var acceptable = tactics(label, "acceptable");
        var uninformative = label.has("uninformative") ? label.get("uninformative").getAsBoolean()
                : acceptable.containsAll(options);
        return new Label(tactics(label, "best"), acceptable, forbidden(label),
                label.has("status") ? label.get("status").getAsString() : "PROPOSED", uninformative);
    }

    private static Set<TacticId> tactics(JsonObject label, String field) {
        var tactics = new LinkedHashSet<TacticId>();
        if (label.has(field)) {
            label.getAsJsonArray(field).forEach(tactic -> tactics.add(new TacticId(tactic.getAsString())));
        }
        return Set.copyOf(tactics);
    }

    /** Format 2 writes a list; format 1 wrote one tactic as text, empty for none. */
    private static Set<TacticId> forbidden(JsonObject label) {
        if (!label.has("forbidden")) {
            return Set.of();
        }
        var value = label.get("forbidden");
        if (value.isJsonArray()) {
            return tactics(label, "forbidden");
        }
        var text = value.getAsString();
        return text.isBlank() ? Set.of() : Set.of(new TacticId(text));
    }

    /** The body to send, with the model set to the one being measured. */
    String bodyFor(String model) {
        var copy = body.deepCopy();
        copy.addProperty("model", model);
        return copy.toString();
    }

    /**
     * A decision request whose questions are those of the document; only the parser reads it, because the body is
     * replayed as recorded.
     */
    DecisionRequest request(TacticalProfile profile) {
        var facts = new CombatFacts(new EncounterId(1), 0, 0, List.of(new MobFacts(MOB, HealthBand.HEALTHY,
                Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of())), List.of(), List.of(), 0);
        return new DecisionRequest(new EncounterId(1), 1, facts, questions, Map.of(MOB, profile));
    }
}
