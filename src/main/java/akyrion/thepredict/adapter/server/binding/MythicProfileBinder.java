package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.adapter.server.context.BukkitSelectorRegistry;
import akyrion.thepredict.adapter.server.context.ParticipantDefaults;
import akyrion.thepredict.adapter.server.context.ParticipantPolicy;
import akyrion.thepredict.adapter.server.context.SelectorRegistry;
import akyrion.thepredict.adapter.server.binding.BindingProblem.Severity;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ShortWindUp;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphPolicy;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.core.skills.MetaSkill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Binds tactical profiles to the loaded MythicMobs mobs, skills and conditions, rejecting what cannot run
 * (M03.3, M03.4). A rejected profile is excluded; the others still bind. Since M17.2 each bound profile also gets its
 * participant policy, compiled from the global settings and the profile's rules at every catalog revision: a selector
 * whose value does not resolve (an unknown type, tag or category) is a {@code WARNING} with its path
 * ({@code participants.entities.allow[2]}, once per binding, or {@code <profile>.Participants.Entities.Allow[0]}) and
 * is ignored. Server thread only.
 */
public final class MythicProfileBinder {
    private final MythicDefinitionLookup definitions;
    private final ParticipantDefaults participants;
    private final SelectorRegistry registry;

    /**
     * Creates the binder over the definitions currently loaded by MythicMobs and the registries of the server.
     *
     * @param participants global participant settings from {@code config.yml} (rule D10)
     */
    public MythicProfileBinder(ParticipantDefaults participants) {
        this(new LiveMythicDefinitions(), participants, new BukkitSelectorRegistry());
    }

    /**
     * Creates the binder over the given definitions with the participant settings of 0.0.1.
     *
     * @param definitions loaded MythicMobs definitions
     */
    public MythicProfileBinder(MythicDefinitionLookup definitions) {
        this(definitions, ParticipantDefaults.STANDARD, new BukkitSelectorRegistry());
    }

    /**
     * Creates the binder.
     *
     * @param definitions loaded MythicMobs definitions
     * @param participants global participant settings
     * @param registry entity types, tags and teams the selectors resolve against; read only for a selector
     */
    public MythicProfileBinder(MythicDefinitionLookup definitions, ParticipantDefaults participants,
                               SelectorRegistry registry) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.participants = Objects.requireNonNull(participants, "participants");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Binds every profile. Only mobs with {@code Predictor: true} appear in the result, but every profile is
     * validated so its errors are visible before the mob is enabled.
     *
     * @param profiles validated profiles keyed by MythicMobs mob ID
     * @return the bound profiles and every problem found
     */
    public BindingResult bind(Map<String, TacticalProfile> profiles) {
        var bound = new LinkedHashMap<String, BoundProfile>();
        var problems = new ArrayList<BindingProblem>();
        var noted = new HashSet<String>();
        var readings = new HashMap<String, NativeSkillReader.Reading>();
        var global = ParticipantPolicy.GlobalSelectors.compile(participants, registry, (path, message) ->
                problems.add(new BindingProblem(Severity.WARNING, Optional.empty(), path, message)));
        for (var profile : profiles.values()) {
            var found = new ArrayList<BindingProblem>();
            try {
                bindOne(profile, global, found, noted, readings)
                        .ifPresent(result -> bound.put(profile.mythicMobId(), result));
            } catch (Rejection rejection) {
                found.add(BindingProblem.of(Severity.ERROR, profile.id(), rejection.path, rejection.getMessage()));
            }
            problems.addAll(found);
        }
        reportEnabledMobsWithoutProfile(profiles, problems);
        return new BindingResult(bound, problems);
    }

    /**
     * Disables the contexts whose custom conditions no plugin resolved. Call it after the tick in which
     * MythicMobs resolves custom conditions (M03.3).
     *
     * @param bound profiles returned by {@link #bind(Map)}
     * @return the same profiles, without the unresolved contexts, and one error per unresolved context
     */
    public BindingResult verifyConditions(Map<String, BoundProfile> bound) {
        var verified = new LinkedHashMap<String, BoundProfile>();
        var problems = new ArrayList<BindingProblem>();
        bound.forEach((mobId, profile) -> {
            var disabled = new HashSet<ContextId>();
            profile.conditions().forEach((context, conditions) -> {
                if (!conditions.unresolvedLines().isEmpty()) {
                    disabled.add(context);
                    problems.add(conditionError(profile.profile().id(), context, conditions.unresolvedLines(),
                            "no plugin resolved the condition"));
                }
            });
            verified.put(mobId, profile.withoutContexts(disabled));
        });
        return new BindingResult(verified, problems);
    }

    private Optional<BoundProfile> bindOne(TacticalProfile profile, ParticipantPolicy.GlobalSelectors global,
                                           List<BindingProblem> problems, Set<String> noted,
                                           Map<String, NativeSkillReader.Reading> readings) {
        var mob = resolveMob(profile);
        var enabled = predictorEnabled(mob).orElseThrow(() -> new Rejection("MythicID",
                "mob " + mob.getInternalName() + " has a Predictor value that is not true or false"));
        var entityType = Objects.toString(mob.getEntityTypeString(), "").toUpperCase(Locale.ROOT);
        var brain = BrainSpecies.contains(entityType);
        if (brain) {
            profile.tactics().values().stream().filter(tactic -> tactic.movement() != Movement.NONE).findFirst()
                    .ifPresent(tactic -> {
                        throw new Rejection("Tactics." + tactic.id() + ".Movement", "species " + entityType
                                + " uses a Brain; only Movement: NONE is allowed");
                    });
        }
        var skills = new HashMap<ChoiceId, Skill>();
        var choices = bindChoices(profile, mob, skills, problems, noted, readings);
        BindingWarnings.language(profile, problems);
        var threat = BindingWarnings.threat(profile, mob, problems);
        var conditions = new HashMap<ContextId, NativeConditions>();
        var disabled = convertConditions(profile, conditions, problems);
        var declared = enabled ? requireDifficultyKeys(mob) : DeclaredDifficulty.NONE;
        var filled = new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                choices, profile.contexts(), profile.movement(), profile.health(), threat, profile.baseAi(),
                profile.timing(), profile.participants(), profile.difficulty(), profile.stances(), profile.telegraph(),
                profile.poise(), profile.zone(), profile.group(), profile.patterns(), profile.memory(),
                profile.decision(), profile.distillMode());
        var lines = skillLines(mob);
        coherence(filled, mob, lines, choices, problems);
        BindingWarnings.tacticFit(filled, problems);
        BindingWarnings.indistinguishable(filled, problems);
        var policy = ParticipantPolicy.compile(participants, global, profile.participants(), registry,
                (path, message) -> problems.add(BindingProblem.of(Severity.WARNING, profile.id(), path, message)));
        var result = new BoundProfile(filled, skills, conditions, entityType, brain, policy, declared.preset(),
                declared.entropy(), declared.training(), MobCueHandlers.read(lines)).withoutContexts(disabled);
        return enabled ? Optional.of(result) : Optional.empty();
    }

    /** The {@code Skills} lines of the mob type, already merged with its templates by MythicMobs. */
    private static List<String> skillLines(MythicMob mob) {
        var lines = mob.getConfig().getStringList("Skills");
        return lines == null ? List.of() : lines;
    }

    /**
     * Warns about a tell the player cannot read, and about {@code ShortWindUp: immediate} (M23.3, decision 4).
     * Effective channels are the skill prefix when the lead is 0, and the mob's {@code PREDICTOR_TELL_} handler otherwise.
     */
    private static void coherence(TacticalProfile profile, MythicMob mob, List<String> lines,
                                  Map<ChoiceId, SkillChoice> choices, List<BindingProblem> problems) {
        var setsStance = false;
        for (var line : lines) {
            if (line.toLowerCase(Locale.ROOT).contains("setstance")) {
                setsStance = true;
                break;
            }
        }
        var channels = new HashMap<String, Integer>();
        var signatures = new HashMap<String, Integer>();
        var settings = profile.telegraph();
        for (var choice : choices.values()) {
            if (!choice.authored()) {
                continue;
            }
            var skill = choice.mythicSkillId().orElse(choice.id().value());
            var danger = DangerRules.classify(choice);
            var answer = DangerRules.answer(choice);
            var wind = choice.busyTicks();
            var shown = TelegraphPolicy.telegraphTicks(wind, danger, answer, settings.againstPlayers(), settings);
            var lead = TelegraphPolicy.leadTicks(shown, wind);
            var bits = lead == 0 ? choice.nativeFacts().cueChannels()
                    : MobCueHandlers.channels(lines, "PREDICTOR_TELL_" + answer.name());
            channels.put(skill, bits);
            signatures.put(skill, choice.nativeFacts().cueSignature());
        }
        BindingWarnings.telegraphCoherence(profile, mob.getInternalName(), setsStance, channels, signatures, problems);
        var selectors = mob.getConfig().getStringList("AITargetSelectors");
        BindingWarnings.group(profile, lines, selectors == null ? List.of() : selectors, problems);
        if (settings.shortWindUp() == ShortWindUp.IMMEDIATE) {
            problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Telegraph.ShortWindUp",
                    "immediate lets a short wind-up skip the minimum; status counts those hits"));
        }
    }

    private MythicMob resolveMob(TacticalProfile profile) {
        var id = profile.mythicMobId();
        return definitions.mob(id).orElseThrow(() -> new Rejection("MythicID", "unknown mob '" + id + "'"
                + suggestion(id, definitions.mobs().stream().map(MythicMob::getInternalName).toList())));
    }

    private Map<ChoiceId, SkillChoice> bindChoices(TacticalProfile profile, MythicMob mob, Map<ChoiceId, Skill> skills,
                                                  List<BindingProblem> problems, Set<String> noted,
                                                  Map<String, NativeSkillReader.Reading> readings) {
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var choice : profile.choices().values()) {
            if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
                choices.put(choice.id(), ChoicePurposes.vanilla(choice));
                continue;
            }
            var path = "Choices." + choice.id() + ".MythicID";
            var skillId = choice.mythicSkillId().orElseThrow();
            var skill = definitions.skill(skillId).orElseThrow(() -> new Rejection(path, "unknown skill '" + skillId
                    + "'" + suggestion(skillId, definitions.skillNames())));
            var reading = readings.computeIfAbsent(skillId, id -> read(id, skill));
            choices.put(choice.id(), ChoicePurposes.skill(profile, choice, skill, reading, noted, problems));
            skills.put(choice.id(), skill);
            BindingWarnings.skill(profile, choice, skill, mob, noted, problems);
        }
        return choices;
    }

    /** Reads a metaskill once; a prepared double is used as-is, and any other skill stays unread. */
    private NativeSkillReader.Reading read(String skillId, Skill skill) {
        var prepared = definitions.preparedReading(skillId);
        if (prepared.isPresent()) {
            return prepared.get();
        }
        return skill instanceof MetaSkill meta ? NativeSkillReader.read(skillId, new LiveNativeSkill(meta))
                : NativeSkillReader.Reading.UNREAD;
    }

    private Set<ContextId> convertConditions(TacticalProfile profile, Map<ContextId, NativeConditions> conditions,
                                             List<BindingProblem> problems) {
        var disabled = new HashSet<ContextId>();
        for (var context : profile.contexts()) {
            if (context.isShortcut()) {
                continue;
            }
            var converted = definitions.conditions(context.nativeConditions());
            conditions.put(context.id(), converted);
            if (!converted.invalidLines().isEmpty()) {
                disabled.add(context.id());
                problems.add(conditionError(profile.id(), context.id(), converted.invalidLines(),
                        "MythicMobs rejected the condition"));
            }
        }
        return disabled;
    }

    private static BindingProblem conditionError(String profileId, ContextId context, List<String> lines, String cause) {
        return BindingProblem.of(Severity.ERROR, profileId, "Contexts." + context + ".Conditions", cause + " '"
                + String.join("', '", lines) + "'; context '" + context + "' is disabled");
    }

    private void reportEnabledMobsWithoutProfile(Map<String, TacticalProfile> profiles, List<BindingProblem> problems) {
        var targeted = new HashSet<String>();
        profiles.values().forEach(profile -> targeted.add(profile.mythicMobId()));
        for (var mob : definitions.mobs()) {
            var id = mob.getInternalName();
            if (targeted.contains(id)) {
                continue;
            }
            var enabled = predictorEnabled(mob);
            if (enabled.isEmpty()) {
                problems.add(new BindingProblem(Severity.ERROR, Optional.empty(), id + ".Predictor",
                        "must be true or false"));
            } else if (enabled.orElseThrow()) {
                problems.add(new BindingProblem(Severity.ERROR, Optional.empty(), id + ".Predictor",
                        "is true but no valid AI profile targets this mob"));
            }
        }
    }

    private static DeclaredDifficulty requireDifficultyKeys(MythicMob mob) {
        var config = mob.getConfig();
        java.util.Optional<akyrion.thepredict.domain.DifficultyPreset> preset = java.util.Optional.empty();
        if (config.isSet("PredictorDifficulty")) {
            var text = config.getString("PredictorDifficulty", "");
            try {
                preset = java.util.Optional.of(akyrion.thepredict.domain.DifficultyPreset.parse(text));
            } catch (IllegalArgumentException exception) {
                throw new Rejection(mob.getInternalName() + ".PredictorDifficulty",
                        "must be one of Fácil, Normal, Difícil, Caos, Vacío");
            }
        }
        var entropy = false;
        if (config.isSet("PredictorEntropy")) {
            var value = config.getBooleanNullable("PredictorEntropy");
            if (value == null) {
                throw new Rejection(mob.getInternalName() + ".PredictorEntropy", "must be true or false");
            }
            entropy = value;
        }
        return new DeclaredDifficulty(preset, entropy, requireTraining(mob));
    }

    private record DeclaredDifficulty(java.util.Optional<akyrion.thepredict.domain.DifficultyPreset> preset,
                                      boolean entropy, boolean training) {
        private static final DeclaredDifficulty NONE = new DeclaredDifficulty(java.util.Optional.empty(), false,
                false);
    }

    private static boolean requireTraining(MythicMob mob) {
        var config = mob.getConfig();
        if (!config.isSet("PredictorTraining")) {
            return false;
        }
        var value = config.getBooleanNullable("PredictorTraining");
        if (value == null) {
            throw new Rejection(mob.getInternalName() + ".PredictorTraining", "must be true or false");
        }
        var predictor = predictorEnabled(mob).orElse(false);
        if (!akyrion.thepredict.domain.learn.TrainingEligibility.legal(predictor, value)) {
            throw new Rejection(mob.getInternalName() + ".PredictorTraining", "requires Predictor: true");
        }
        return value;
    }

    private static Optional<Boolean> predictorEnabled(MythicMob mob) {
        if (!mob.getConfig().isSet("Predictor")) {
            return Optional.of(false);
        }
        return Optional.ofNullable(mob.getConfig().getBooleanNullable("Predictor"));
    }

    private static String suggestion(String id, Collection<String> names) {
        return names.stream().filter(name -> !name.equals(id) && name.equalsIgnoreCase(id)).findFirst()
                .map(name -> "; IDs are case-sensitive, did you mean '" + name + "'?").orElse("");
    }

    private static final class Rejection extends RuntimeException {
        private final String path;

        private Rejection(String path, String message) {
            super(message, null, false, false);
            this.path = path;
        }
    }
}
