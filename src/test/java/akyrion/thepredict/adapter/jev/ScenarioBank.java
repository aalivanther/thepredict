package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.BankScenario.BankProfile;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.Eligibility;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OptionSet;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * The labelled bank of M16.9, generated from {@link BankMatrix}: one request per scenario with the example guardian,
 * the bank skirmisher or the shield test profile of M15.8, all with declared purposes; the options are the eligible
 * tactics of the capture (the distinct ones by priority, at most {@value #MAX_OPTIONS}); and one document per scenario
 * in {@code src/test/resources/scenarios/bank/} in format 2 with the compact body, the options and the labels. The
 * bench encodes each scenario again at the level of each arm (M16.10); the labels stay in the documents, where
 * {@link LabelReviewApply} writes the review of the user.
 */
final class ScenarioBank {
    /** Folder of the bank. */
    static final Path FOLDER = Path.of("src", "test", "resources", "scenarios", "bank");
    /** Review file that the user edits. */
    static final Path REVIEW = FOLDER.resolve("label-review.yml");
    /** Options of one question, as {@code decision.maxOptionsPerQuestion} by default. */
    static final int MAX_OPTIONS = OptionSet.DEFAULT_MAX_OPTIONS;
    /** Layout of the documents: 1 is M13, 2 adds best, a forbidden list, rationale and review status. */
    static final int FORMAT = 2;
    private static final EncounterId ENCOUNTER = new EncounterId(1);
    private static final double RANGED_REACH_GAP_BLOCKS = 7.0;
    private static final Map<BankProfile, TacticalProfile> PROFILES = new EnumMap<>(BankProfile.class);

    private ScenarioBank() {
    }

    /** Every scenario, in identifier order. */
    static List<BankScenario> scenarios() {
        return BankMatrix.rows();
    }

    /** One scenario by identifier. */
    static BankScenario scenario(String id) {
        return scenarios().stream().filter(scenario -> scenario.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no scenario " + id + " in the bank"));
    }

    /**
     * The identifiers of a named set: {@code core12} (the first of each family), {@code core24} (the first two),
     * {@code all}, or a comma-separated list of identifiers.
     */
    static List<String> set(String name) {
        var trimmed = name.trim();
        var all = scenarios().stream().map(BankScenario::id).toList();
        return switch (trimmed) {
            case "all" -> all;
            case "core12" -> all.stream().filter(id -> id.endsWith("-01")).toList();
            case "core24" -> all.stream().filter(id -> id.endsWith("-01") || id.endsWith("-02")).toList();
            default -> {
                var ids = new ArrayList<String>();
                for (var id : trimmed.split(",")) {
                    ids.add(scenario(id.trim()).id());
                }
                yield List.copyOf(ids);
            }
        };
    }

    /** The named sets a scenario belongs to, for the documents and the review sheet. */
    static List<String> setsOf(String id) {
        var sets = new ArrayList<String>();
        if (set("core12").contains(id)) {
            sets.add("core12");
        }
        if (set("core24").contains(id)) {
            sets.add("core24");
        }
        return sets;
    }

    /** The profile of a scenario at a detail, with or without fit checks. */
    static TacticalProfile profile(BankProfile profile, QuestionDetail detail, boolean fitChecks) {
        return JevFixtures.detailed(base(profile), detail, fitChecks);
    }

    /** The options of a scenario's question, eligible under its situation. */
    static List<TacticId> options(BankScenario scenario) {
        return request(scenario, QuestionDetail.COMPACT, false).questions().getFirst().options();
    }

    /**
     * The request of one scenario: one mob, its only question keyed {@code q0} and, when the profile sends fit checks
     * and the question is {@code NOW}, one fit check per option keyed from {@code q1}.
     */
    static DecisionRequest request(BankScenario scenario, QuestionDetail detail, boolean fitChecks) {
        var profile = profile(scenario.profile(), detail, fitChecks);
        var key = new MobKey(new UUID(0xBA4CL, scenarios().indexOf(scenario) + 1), 0);
        var mob = mobFacts(scenario, profile, key);
        var options = ordered(scenario.situation() == Situation.NOW ? mob.eligibleTactics()
                : mob.eligibleIn(scenario.situation()), profile);
        var question = new QuestionSpec(QuestionKey.of(0), key, scenario.situation(), options);
        var checks = new ArrayList<FitCheck>();
        if (scenario.situation() == Situation.NOW && profile.timing().sendsFitChecks()) {
            for (var option : options) {
                checks.add(new FitCheck(QuestionKey.of(checks.size() + 1), key, option));
            }
        }
        var facts = new CombatFacts(ENCOUNTER, 1000, 50_000_000_000L, List.of(mob), participants(scenario), List.of(),
                20);
        return new DecisionRequest(ENCOUNTER, 1, facts, List.of(question), Map.of(key, profile), checks);
    }

    /** The document of one scenario with its proposed labels, or with the labels of a review when there is one. */
    static String document(BankScenario scenario, Optional<JsonObject> reviewed) {
        var request = request(scenario, QuestionDetail.COMPACT, false);
        var body = new RequestEncoder(LiveJevGate.MODEL, new SemanticEncoder(12_000)).encode(request);
        var document = new JsonObject();
        document.addProperty("format", FORMAT);
        document.addProperty("id", scenario.id());
        document.addProperty("family", scenario.family().key());
        document.addProperty("profile", request.profiles().values().iterator().next().id());
        var sets = new JsonArray();
        setsOf(scenario.id()).forEach(sets::add);
        document.add("sets", sets);
        document.addProperty("requestChars", body.length());
        document.addProperty("mobs", 1);
        document.addProperty("participants", scenario.participants());
        document.add("request", StrictJson.parseObject(body, "bank body"));
        var question = new JsonObject();
        question.addProperty("key", "q0");
        question.addProperty("situation", scenario.situation().name());
        question.add("options", array(request.questions().getFirst().options().stream().map(TacticId::value).toList()));
        var questions = new JsonArray();
        questions.add(question);
        document.add("questions", questions);
        var labels = new JsonObject();
        labels.add("q0", reviewed.filter(label -> !"PROPOSED".equals(label.get("status").getAsString()))
                .orElseGet(() -> proposed(scenario, request.questions().getFirst().options())));
        document.add("labels", labels);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document) + "\n";
    }

    /** The document of a scenario as it should be on disk now: its reviewed labels when the file holds a review. */
    static String document(BankScenario scenario) {
        return document(scenario, stored(scenario));
    }

    /** The label stored in the document of a scenario, if the file exists. */
    static Optional<JsonObject> stored(BankScenario scenario) {
        var file = FOLDER.resolve(fileName(scenario));
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(StrictJson.parseObject(Files.readString(file), "bank document")
                    .getAsJsonObject("labels").getAsJsonObject("q0"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** File name of a scenario. */
    static String fileName(BankScenario scenario) {
        return scenario.id() + ".json";
    }

    /**
     * Whether a label cannot inform the accuracy: every option is acceptable, or no option is left that is not.
     */
    static boolean uninformative(List<String> acceptable, List<TacticId> options) {
        return options.stream().allMatch(option -> acceptable.contains(option.value()));
    }

    /** The label a scenario proposes, before any review. */
    static JsonObject proposed(BankScenario scenario, List<TacticId> options) {
        var label = new JsonObject();
        label.add("best", array(scenario.best()));
        label.add("acceptable", array(scenario.acceptable()));
        label.add("forbidden", array(scenario.forbidden()));
        label.addProperty("rationale", scenario.rationale());
        label.addProperty("status", "PROPOSED");
        label.addProperty("reviewedBy", "");
        label.addProperty("reviewedOn", "");
        label.addProperty("uninformative", uninformative(scenario.acceptable(), options));
        return label;
    }

    static JsonArray array(List<String> values) {
        var array = new JsonArray(values.size());
        values.forEach(array::add);
        return array;
    }

    private static MobFacts mobFacts(BankScenario scenario, TacticalProfile profile, MobKey key) {
        var relation = new TargetRelation(new ParticipantAlias(1), scenario.distance(), scenario.trend(),
                scenario.reachable(), scenario.visible(), false, scenario.flankable(), scenario.participants() > 1);
        var outOfReach = outOfReach(profile, scenario.distance());
        var movements = EnumSet.allOf(Movement.class);
        if (!scenario.flankable()) {
            movements.remove(Movement.FLANK);
        }
        var ready = profile.choices().keySet();
        var active = contexts(profile, scenario.mobHealth(), scenario.distance());
        var eligible = Eligibility.eligible(profile, active, ready, movements, outOfReach);
        var hypothetical = new EnumMap<Situation, Set<TacticId>>(Situation.class);
        hypothetical.put(Situation.IF_TARGET_IN_MELEE, Eligibility.eligible(profile, contexts(profile,
                scenario.mobHealth(), DistanceBand.MELEE), ready, movements, outOfReach(profile, DistanceBand.MELEE)));
        hypothetical.put(Situation.IF_OWN_HEALTH_CRITICAL, Eligibility.eligible(profile, contexts(profile,
                HealthBand.CRITICAL, scenario.distance()), ready, movements, outOfReach));
        var cues = scenario.visible() ? Set.<MobCue>of() : Set.of(MobCue.NO_LINE_OF_SIGHT);
        return new MobFacts(key, scenario.mobHealth(), Optional.of(relation), active, eligible, cues, outOfReach,
                hypothetical);
    }

    /** The shortcut contexts that hold, as {@code ShortcutFacts} of the capture evaluates them (M07.7). */
    private static Set<ContextId> contexts(TacticalProfile profile, HealthBand health, DistanceBand distance) {
        var active = new LinkedHashSet<ContextId>();
        for (var context : profile.contexts()) {
            var holds = switch (context.id().value()) {
                case "attacking_phase" -> distance == DistanceBand.MELEE;
                case "tracking_enemy" -> distance != DistanceBand.MELEE;
                case "low_health_phase" -> health == HealthBand.CRITICAL;
                default -> false;
            };
            if (holds) {
                active.add(context.id());
            }
        }
        return active;
    }

    /**
     * The declared choices whose reach misses a target of a band: none in {@code MELEE}; in {@code RANGED}, the target is
     * taken at {@value #RANGED_REACH_GAP_BLOCKS} blocks; in {@code BEYOND}, beyond ranged reach.
     */
    private static Set<ChoiceId> outOfReach(TacticalProfile profile, DistanceBand distance) {
        var gap = switch (distance) {
            case MELEE -> 0.0;
            case RANGED -> RANGED_REACH_GAP_BLOCKS;
            case BEYOND -> profile.movement().rangedReachBlocks() + 1.0;
        };
        var out = new LinkedHashSet<ChoiceId>();
        for (var choice : profile.choices().values()) {
            var purpose = choice.purpose().filter(SkillPurpose::authored).orElse(null);
            var reach = purpose == null ? OptionalDouble.empty() : purpose.effectiveReachBlocks();
            if (purpose != null && reach.isPresent() && reach.getAsDouble() < gap) {
                out.add(choice.id());
            }
        }
        return out;
    }

    private static List<ParticipantFacts> participants(BankScenario scenario) {
        var participants = new ArrayList<ParticipantFacts>();
        participants.add(new ParticipantFacts(new ParticipantAlias(1), scenario.targetHealth(), ArmorBand.MEDIUM,
                scenario.cues(), 1));
        for (var index = 2; index <= scenario.participants(); index++) {
            participants.add(new ParticipantFacts(new ParticipantAlias(index), HealthBand.HEALTHY, ArmorBand.LIGHT,
                    Set.of(), index));
        }
        return participants;
    }

    private static List<TacticId> ordered(Set<TacticId> eligible, TacticalProfile profile) {
        var sorted = eligible.stream().sorted(Comparator.comparingInt((TacticId id) -> -profile.tactics().get(id)
                .priority()).thenComparing(TacticId::value)).toList();
        return OptionSet.capped(OptionSet.distinct(sorted, profile), MAX_OPTIONS);
    }

    private static synchronized TacticalProfile base(BankProfile profile) {
        return PROFILES.computeIfAbsent(profile, key -> switch (key) {
            case GUARDIAN -> ScenarioFacts.example();
            case GUARDIAN_WITHOUT_LOW_HEALTH -> withoutLowHealth(ScenarioFacts.example());
            case SKIRMISHER -> load("/scenarios/bank/profiles/bank-skirmisher.yml", "bank-skirmisher", "BankSkirmisher");
            case SHIELD -> load("/profiles/purpose-shield.yml", "purpose-shield", "PurposeShieldMob");
        });
    }

    private static TacticalProfile withoutLowHealth(TacticalProfile profile) {
        var contexts = profile.contexts().stream().filter(context -> !context.id().value().equals("low_health_phase"))
                .toList();
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), contexts, profile.movement(), profile.health(), profile.threat(), profile.baseAi(),
                profile.timing());
    }

    private static TacticalProfile load(String resource, String name, String mythicMobId) {
        try (var stream = ScenarioBank.class.getResourceAsStream(resource)) {
            var directory = Files.createTempDirectory("thepredict-bank");
            var file = directory.resolve(name + ".yml");
            Files.copy(Objects.requireNonNull(stream, resource), file);
            var profile = new ProfileLoader().load(directory).profiles().get(mythicMobId);
            Files.delete(file);
            Files.delete(directory);
            return Objects.requireNonNull(profile, name);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
