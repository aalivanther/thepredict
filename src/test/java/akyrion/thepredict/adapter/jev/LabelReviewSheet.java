package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.OptionContext;
import akyrion.thepredict.domain.QuestionDetail;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The review sheet of the labelled bank (M16.9, step 1): {@code ./gradlew labelReviewSheet --offline} writes
 * {@code build/reports/m16/label-review.md}, which the user reads without opening any JSON. For each scenario: its sets,
 * the state as the phrases Jev reads (the facts of the target once, inside the mob), the question, each option described as the {@code standard} detail does but
 * without what fits or fits poorly now (so the label is never written from what the code computes, M16.2), the proposed
 * labels with their reason, and the entry to copy into {@code label-review.yml}. No network and no cost.
 */
final class LabelReviewSheet {
    /** Where the sheet is written by default. */
    static final Path OUTPUT = Path.of("build", "reports", "m16", "label-review.md");

    private LabelReviewSheet() {
    }

    /**
     * Writes the sheet.
     *
     * @param arguments the output path, {@link #OUTPUT} by default
     * @throws IOException if the sheet cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var output = arguments.length > 0 ? Path.of(arguments[0]) : OUTPUT;
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, sheet());
        System.out.println("Label review sheet: " + output.toAbsolutePath());
    }

    /** The whole sheet: the procedure, then the core 24 scenarios, then the other 36. */
    static String sheet() {
        var text = new StringBuilder();
        text.append("# M16 label review\n\n");
        text.append("How to review, without editing any JSON and without network or cost:\n\n");
        text.append("1. Read each scenario below. The options are described as Jev reads them, without what the code"
                + " thinks fits now, so your judgement stays your own.\n");
        text.append("2. Edit `src/test/resources/scenarios/bank/label-review.yml`: set `reviewer:` to your name and, for"
                + " each scenario you reviewed, `status: REVIEWED` (you agree, maybe after your changes) or"
                + " `status: DISPUTED` (the scenario itself is wrong). Only if you change something, add `best:`,"
                + " `acceptable:`, `forbidden:` (lists of option names) and `note:`.\n");
        text.append("3. Run `./gradlew applyLabelReview --offline`: it writes the review into each document and checks"
                + " every rule (best inside acceptable, nothing both acceptable and forbidden, only offered options).\n");
        text.append("4. A preliminary re-bench needs the 24 core scenarios (`core24`, about 20 to 30 minutes of review);"
                + " changing a default needs all 60 (45 to 60 minutes).\n\n");
        var core = ScenarioBank.set("core24");
        text.append("## Core scenarios (core24)\n\n");
        ScenarioBank.scenarios().stream().filter(scenario -> core.contains(scenario.id()))
                .forEach(scenario -> scenario(text, scenario));
        text.append("## Other scenarios\n\n");
        ScenarioBank.scenarios().stream().filter(scenario -> !core.contains(scenario.id()))
                .forEach(scenario -> scenario(text, scenario));
        return text.toString();
    }

    private static void scenario(StringBuilder text, BankScenario scenario) {
        var request = ScenarioBank.request(scenario, QuestionDetail.STANDARD, false);
        var profile = request.profiles().values().iterator().next();
        var question = request.questions().getFirst();
        var label = ScenarioBank.stored(scenario).orElseGet(() -> ScenarioBank.proposed(scenario, question.options()));
        var sets = ScenarioBank.setsOf(scenario.id());
        text.append("### ").append(scenario.id()).append(sets.isEmpty() ? "" : " (" + String.join(", ", sets) + ")")
                .append("\n\n");
        text.append("Family `").append(scenario.family().key()).append("`, profile `").append(profile.id())
                .append("`, status **").append(label.get("status").getAsString()).append("**")
                .append(label.get("uninformative").getAsBoolean() ? ", uninformative (every option is acceptable)" : "")
                .append(".\n\n");
        text.append("State:\n\n");
        var state = new SemanticEncoder(12_000).encodeState(request.facts(), request.profiles(),
                Set.of(question.mob()));
        facts(state).forEach(fact -> text.append("- ").append(fact).append('\n'));
        text.append("\nQuestion: ").append(SemanticPhrases.question(question.situation(), "mob_1")).append("\n\n");
        text.append("Options:\n\n");
        for (var option : question.options()) {
            var tactic = profile.tactic(option).orElseThrow();
            var criterion = CriterionRenderer.render(tactic, profile, QuestionDetail.STANDARD,
                    OptionContext.OptionFit.NONE, Set.of()).json().getAsJsonObject();
            text.append("- `").append(option.value()).append("`: ").append(readable(criterion)).append('\n');
        }
        text.append("\nProposed: best ").append(names(label, "best")).append("; acceptable ")
                .append(names(label, "acceptable")).append("; forbidden ").append(names(label, "forbidden"))
                .append(".\n\nReason: ").append(label.get("rationale").getAsString()).append("\n\n");
        text.append("```yaml\n  ").append(scenario.id()).append(":\n    status: REVIEWED\n```\n\n");
    }

    private static List<String> facts(JsonObject state) {
        var facts = new ArrayList<String>();
        var encounter = state.getAsJsonObject("encounter");
        facts.add(encounter.get("duration").getAsString());
        facts.add(encounter.get("headcount").getAsString());
        for (var mob : state.getAsJsonArray("mobs")) {
            var entry = mob.getAsJsonObject();
            Optional.ofNullable(entry.get("objective")).ifPresent(objective -> facts.add("objective of "
                    + entry.get("ref").getAsString() + ": " + objective.getAsString()));
            entry.getAsJsonArray("facts").forEach(fact -> facts.add(fact.getAsString()));
            Optional.ofNullable(entry.getAsJsonObject("target")).ifPresent(target -> target.getAsJsonArray("facts")
                    .forEach(fact -> facts.add(fact.getAsString())));
        }
        var targets = new HashSet<String>();
        for (var mob : state.getAsJsonArray("mobs")) {
            Optional.ofNullable(mob.getAsJsonObject().getAsJsonObject("target"))
                    .ifPresent(target -> targets.add(target.get("ref").getAsString()));
        }
        for (var participant : state.getAsJsonArray("participants")) {
            var entry = participant.getAsJsonObject();
            if (!targets.contains(entry.get("ref").getAsString())) {
                entry.getAsJsonArray("facts").forEach(fact -> facts.add(fact.getAsString()));
            }
        }
        state.getAsJsonArray("recent_events").forEach(event -> facts.add("recently: " + event.getAsString()));
        return facts;
    }

    private static String readable(JsonObject criterion) {
        var parts = new ArrayList<String>();
        for (var entry : criterion.entrySet()) {
            parts.add(entry.getKey().replace('_', ' ') + ": " + text(entry.getValue()));
        }
        return String.join("; ", parts);
    }

    private static String text(JsonElement value) {
        if (value.isJsonArray()) {
            var items = new ArrayList<String>();
            value.getAsJsonArray().forEach(item -> items.add(item.getAsString()));
            return String.join(", ", items);
        }
        return value.getAsString();
    }

    private static String names(JsonObject label, String field) {
        var names = new ArrayList<String>();
        label.getAsJsonArray(field).forEach(name -> names.add("`" + name.getAsString() + "`"));
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
