package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.OptionContext;
import akyrion.thepredict.domain.OptionPruning;
import akyrion.thepredict.domain.OptionSet;
import akyrion.thepredict.domain.PruneReason;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One pass of M19.9 E1 over the labelled bank. Pure aside from reading the bank files. No clock.
 */
final class BankPruningScan {
    private static final RequestEncoder ENCODER = new RequestEncoder(LiveJevGate.MODEL, new SemanticEncoder(12_000));

    private BankPruningScan() {
    }

    /** One stored scenario and the live request it measures. */
    record Scene(BankScenario scenario, DecisionRequest request, JsonObject label, List<String> fileOptions) {
    }

    /** Counts of one configuration. Reason keys stay in enum order. */
    static final class Tally {
        final String name;
        final CastMode mode;
        final PruningMode pruning;
        final int cap;
        int reviewed;
        int bestApplicable;
        int bestKept;
        int acceptableBase;
        int acceptableKept;
        int uniqueRemoved;
        int forbiddenRemoved;
        int curesRemoved;
        int healthySustainRemoved;
        int local;
        int undone;
        long chars;
        final EnumMap<PruneReason, Integer> reasons = new EnumMap<>(PruneReason.class);
        final List<String> mismatches = new ArrayList<>();
        final List<String> losses = new ArrayList<>();

        Tally(CastMode mode, PruningMode pruning, int cap) {
            this.mode = mode;
            this.pruning = pruning;
            this.cap = cap;
            name = mode.name() + " " + pruning.name() + " cap " + cap;
        }
    }

    /** Every bank scenario, in identifier order. */
    static List<Scene> load() {
        var scenes = new ArrayList<Scene>();
        var scenarios = new ArrayList<>(ScenarioBank.scenarios());
        scenarios.sort(Comparator.comparing(BankScenario::id));
        for (var scenario : scenarios) {
            scenes.add(scene(scenario));
        }
        return List.copyOf(scenes);
    }

    /** Measures one pruning, cap and cast mode against the stored labels. */
    static Tally measure(List<Scene> scenes, CastMode mode, PruningMode pruning, int cap) {
        var tally = new Tally(mode, pruning, cap);
        for (var scene : scenes) {
            accumulate(tally, scene, apply(scene, mode, pruning, cap));
        }
        tally.losses.sort(Comparator.naturalOrder());
        tally.mismatches.sort(Comparator.naturalOrder());
        return tally;
    }

    private static Scene scene(BankScenario scenario) {
        var request = ScenarioBank.request(scenario, akyrion.thepredict.domain.QuestionDetail.FULL, false);
        var label = ScenarioBank.stored(scenario).orElseThrow(() -> new IllegalStateException("no label " + scenario.id()));
        return new Scene(scenario, request, label, fileOptions(scenario));
    }

    private static List<String> fileOptions(BankScenario scenario) {
        var file = ScenarioBank.FOLDER.resolve(ScenarioBank.fileName(scenario));
        try {
            var document = StrictJson.parseObject(Files.readString(file, StandardCharsets.UTF_8), "bank document");
            var options = document.getAsJsonArray("questions").get(0).getAsJsonObject().getAsJsonArray("options");
            var values = new ArrayList<String>();
            options.forEach(option -> values.add(option.getAsString()));
            return List.copyOf(values);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Kept apply(Scene scene, CastMode mode, PruningMode pruning, int cap) {
        var question = scene.request.questions().getFirst();
        var profile = scene.request.profiles().values().iterator().next();
        var ordered = question.options();
        if (pruning != PruningMode.SAFE) {
            return new Kept(OptionSet.capped(OptionSet.distinct(ordered, profile), cap), Map.of(), false);
        }
        var mob = scene.request.facts().mobs().getFirst();
        var now = OptionContext.hypothetical(ActiveUseConditions.of(mob, scene.request.facts().participants()),
                question.situation());
        var result = OptionPruning.prune(ordered, retimed(profile, mode), mob, now, mode, cap);
        return new Kept(result.kept(), result.dropped(), result.vetoesUndone());
    }

    /** The same profile with another cast mode. The horizon stays the one the profile already has. */
    static TacticalProfile retimed(TacticalProfile profile, CastMode mode) {
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), profile.contexts(), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), profile.timing().withCasting(mode, profile.timing().readyHorizonTicks()),
                profile.participants());
    }

    private static void accumulate(Tally tally, Scene scene, Kept kept) {
        var profile = scene.request.profiles().values().iterator().next();
        var baseline = scene.request.questions().getFirst().options().stream().map(TacticId::value).toList();
        var keptIds = kept.ids.stream().map(TacticId::value).toList();
        if ("REVIEWED".equals(scene.label.get("status").getAsString())) {
            tally.reviewed++;
        }
        if (tally.pruning == PruningMode.OFF && tally.cap == OptionSet.DEFAULT_MAX_OPTIONS && !baseline.equals(
                scene.fileOptions)) {
            tally.mismatches.add(scene.scenario.id());
        }
        quality(tally, scene, baseline, keptIds, kept);
        drops(tally, scene, profile, kept);
        if (kept.ids.size() < QuestionSpec.MIN_OPTIONS) {
            tally.local++;
            return;
        }
        var question = scene.request.questions().getFirst().withOptions(kept.ids);
        var request = new DecisionRequest(scene.request.encounter(), scene.request.sequence(), scene.request.facts(),
                List.of(question), scene.request.profiles());
        tally.chars += JevMeasurements.bodyChars(ENCODER, request);
    }

    private static void quality(Tally tally, Scene scene, List<String> baseline, List<String> keptIds, Kept kept) {
        var best = strings(scene.label, "best");
        var acceptable = strings(scene.label, "acceptable");
        for (var id : best) {
            if (baseline.contains(id)) {
                tally.bestApplicable++;
                if (keptIds.contains(id)) {
                    tally.bestKept++;
                } else {
                    tally.losses.add(scene.scenario.id() + " best " + id + " " + reasonOf(kept, id));
                }
            }
        }
        var present = acceptable.stream().filter(baseline::contains).toList();
        if (!present.isEmpty()) {
            tally.acceptableBase++;
            if (present.stream().anyMatch(keptIds::contains)) {
                tally.acceptableKept++;
            }
        }
        if (present.size() == 1 && !keptIds.contains(present.getFirst())) {
            tally.uniqueRemoved++;
            tally.losses.add(scene.scenario.id() + " unique " + present.getFirst());
        }
        for (var id : strings(scene.label, "forbidden")) {
            if (baseline.contains(id) && !keptIds.contains(id)) {
                tally.forbiddenRemoved++;
            }
        }
    }

    private static void drops(Tally tally, Scene scene, TacticalProfile profile, Kept kept) {
        if (kept.undone) {
            tally.undone++;
        }
        for (var dropped : kept.dropped.entrySet()) {
            tally.reasons.merge(dropped.getValue(), 1, Integer::sum);
            if (dropped.getValue() != PruneReason.SUSTAIN_WHILE_HEALTHY || !sustain(profile, dropped.getKey())) {
                continue;
            }
            var id = dropped.getKey().value();
            var bestOrAcceptable = strings(scene.label, "best").contains(id) || strings(scene.label, "acceptable")
                    .contains(id);
            if (bestOrAcceptable) {
                tally.curesRemoved++;
                tally.losses.add(scene.scenario.id() + " cure " + id);
            }
            if (scene.scenario.mobHealth() == HealthBand.HEALTHY && labelled(scene, id)) {
                tally.healthySustainRemoved++;
            }
        }
    }

    private static String reasonOf(Kept kept, String id) {
        for (var dropped : kept.dropped.entrySet()) {
            if (dropped.getKey().value().equals(id)) {
                return dropped.getValue().name();
            }
        }
        return "absent";
    }

    private static boolean labelled(Scene scene, String id) {
        return strings(scene.label, "best").contains(id) || strings(scene.label, "acceptable").contains(id)
                || strings(scene.label, "forbidden").contains(id);
    }

    private static boolean sustain(TacticalProfile profile, TacticId id) {
        var choice = profile.tactic(id).flatMap(akyrion.thepredict.domain.Tactic::choice).map(profile.choices()::get)
                .orElse(null);
        return choice != null && choice.authored() && choice.isTyped() && choice.typedPurpose().primary() == SkillRole.SUSTAIN;
    }

    private static List<String> strings(JsonObject label, String field) {
        var values = new ArrayList<String>();
        label.getAsJsonArray(field).forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private record Kept(List<TacticId> ids, Map<TacticId, PruneReason> dropped, boolean undone) {
    }
}
