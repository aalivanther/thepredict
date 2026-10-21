package akyrion.thepredict.adapter.jev;

import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Applies the label review of the user (M16.9, step 3): {@code ./gradlew applyLabelReview --offline} reads
 * {@code src/test/resources/scenarios/bank/label-review.yml} and writes each entry into its bank document, with
 * {@code reviewedBy} from the field {@code reviewer:} and today's date, and then {@code ScenarioBankTest} checks every
 * rule. An entry left {@code PROPOSED} without changes keeps the proposal; {@code REVIEWED} or {@code DISPUTED} needs a
 * reviewer; {@code best}, {@code acceptable}, {@code forbidden} and {@code note} replace the proposal only when given. A
 * label that did not change keeps its review date. Every entry is validated and every document built before the first
 * write, and each document is written to a temporary file and moved into place, so an invalid review changes nothing.
 * No network and no cost; the build never runs it.
 */
final class LabelReviewApply {
    /** Statuses a label may have. */
    static final Set<String> STATUSES = Set.of("PROPOSED", "REVIEWED", "DISPUTED");
    private static final Set<String> FIELDS = Set.of("status", "best", "acceptable", "forbidden", "note");

    private LabelReviewApply() {
    }

    /**
     * Applies the review file of the bank to its documents.
     *
     * @param arguments unused
     * @throws IOException if a file cannot be read or written
     */
    public static void main(String[] arguments) throws IOException {
        var changed = apply(ScenarioBank.FOLDER, ScenarioBank.REVIEW, LocalDate.now(ZoneOffset.UTC));
        System.out.println("Label review applied: " + changed.size() + " documents written " + changed);
    }

    /**
     * Applies one review file to the documents of a folder.
     *
     * @param folder folder of the bank documents
     * @param review the review file
     * @param today date written into the reviewed labels
     * @return the identifiers whose document changed
     * @throws IOException if a file cannot be read or written
     * @throws IllegalArgumentException naming the scenario and field of an invalid entry
     */
    static List<String> apply(Path folder, Path review, LocalDate today) throws IOException {
        var yaml = new YamlConfiguration();
        try {
            yaml.load(review.toFile());
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("label-review.yml is not valid YAML: " + exception.getMessage());
        }
        var reviewer = yaml.getString("reviewer", "").trim();
        var entries = yaml.getConfigurationSection("scenarios");
        if (entries == null) {
            throw new IllegalArgumentException("label-review.yml needs a section scenarios");
        }
        var pending = documents(folder, entries, reviewer, today);
        for (var write : pending.entrySet()) {
            replace(write.getKey(), write.getValue());
        }
        return pending.keySet().stream().map(file -> file.getFileName().toString().replaceFirst("\\.json$", ""))
                .toList();
    }

    /**
     * Validates every entry and builds every changed document before anything is written, so an invalid entry leaves
     * all the documents as they were.
     */
    private static Map<Path, String> documents(Path folder, ConfigurationSection entries, String reviewer,
                                               LocalDate today) throws IOException {
        var pending = new LinkedHashMap<Path, String>();
        for (var id : entries.getKeys(false)) {
            var scenario = ScenarioBank.scenario(id);
            var entry = entries.getConfigurationSection(id);
            if (entry == null) {
                throw new IllegalArgumentException(id + " must be a section with status");
            }
            var label = label(scenario, entry, reviewer, today);
            var file = folder.resolve(ScenarioBank.fileName(scenario));
            var previous = Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.<String>empty();
            var kept = previous.map(text -> StrictJson.parseObject(text, id).getAsJsonObject("labels")
                    .getAsJsonObject("q0")).filter(stored -> sameReview(stored, label)).orElse(label);
            var document = ScenarioBank.document(scenario, Optional.of(kept));
            if (previous.filter(document::equals).isEmpty()) {
                pending.put(file, document);
            }
        }
        return pending;
    }

    /** Writes a document beside its target and moves it into place, so a document is never left half written. */
    private static void replace(Path file, String document) throws IOException {
        var temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, document);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject label(BankScenario scenario, ConfigurationSection entry, String reviewer,
                                    LocalDate today) {
        for (var field : entry.getKeys(false)) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException(scenario.id() + "." + field + " is not a known field");
            }
        }
        var status = entry.getString("status", "").trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException(scenario.id() + ".status must be PROPOSED, REVIEWED or DISPUTED");
        }
        if (!status.equals("PROPOSED") && reviewer.isEmpty()) {
            throw new IllegalArgumentException("reviewer must name who reviewed " + scenario.id());
        }
        var options = ScenarioBank.options(scenario).stream().map(option -> option.value()).toList();
        var best = list(entry, "best", scenario.best(), options, scenario.id());
        var acceptable = list(entry, "acceptable", scenario.acceptable(), options, scenario.id());
        var forbidden = list(entry, "forbidden", scenario.forbidden(), options, scenario.id());
        if (!acceptable.containsAll(best)) {
            throw new IllegalArgumentException(scenario.id() + ".best must be part of acceptable");
        }
        var overlap = new HashSet<>(acceptable);
        overlap.retainAll(forbidden);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(scenario.id() + " has tactics both acceptable and forbidden: " + overlap);
        }
        var label = new JsonObject();
        label.add("best", ScenarioBank.array(best));
        label.add("acceptable", ScenarioBank.array(acceptable));
        label.add("forbidden", ScenarioBank.array(forbidden));
        label.addProperty("rationale", scenario.rationale());
        label.addProperty("status", status);
        label.addProperty("reviewedBy", status.equals("PROPOSED") ? "" : reviewer);
        label.addProperty("reviewedOn", status.equals("PROPOSED") ? "" : today.toString());
        label.addProperty("uninformative", ScenarioBank.uninformative(acceptable, ScenarioBank.options(scenario)));
        var note = entry.getString("note", "").trim();
        if (!note.isEmpty()) {
            label.addProperty("note", note);
        }
        return label;
    }

    private static List<String> list(ConfigurationSection entry, String field, List<String> proposed,
                                     List<String> options, String id) {
        if (!entry.contains(field)) {
            return proposed;
        }
        var values = new ArrayList<String>();
        for (var value : entry.getStringList(field)) {
            var tactic = value.trim();
            if (!options.contains(tactic)) {
                throw new IllegalArgumentException(id + "." + field + " names '" + tactic + "', which is not an option: "
                        + options);
            }
            if (!values.contains(tactic)) {
                values.add(tactic);
            }
        }
        return List.copyOf(values);
    }

    /** Two labels with the same content and status, whatever their review date. */
    private static boolean sameReview(JsonObject previous, JsonObject next) {
        var left = previous.deepCopy();
        var right = next.deepCopy();
        left.remove("reviewedOn");
        right.remove("reviewedOn");
        return left.equals(right);
    }
}
