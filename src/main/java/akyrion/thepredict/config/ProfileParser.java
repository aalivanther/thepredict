package akyrion.thepredict.config;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemoryProfile;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternProfile;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZoneSettings;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Converts one schema 2 profile section into a {@link TacticalProfile} (M03.2). Choices, tactics and
 * identity live here; contexts and the numeric sections live in {@link ProfileSectionParser}.
 */
final class ProfileParser {
    private final ProfileSectionParser sections;
    private final ParticipantSettings participants;
    private final ZoneMode globalZone;
    private final GroupMode globalGroup;
    private final PatternMode globalPatterns;
    private final MemoryMode globalMemory;
    private final akyrion.thepredict.domain.distill.DistillMode globalDistill;

    /** Creates the parser with the inherited defaults. */
    ProfileParser(PluginSettings settings) {
        this.sections = new ProfileSectionParser(Objects.requireNonNull(settings, "settings"));
        this.participants = settings.participants();
        this.globalZone = settings.zone().mode();
        this.globalGroup = settings.group().mode();
        this.globalPatterns = settings.patterns().mode();
        this.globalMemory = settings.memory().mode();
        this.globalDistill = settings.distill().mode();
    }

    /**
     * Parses one profile. The skill {@code PredictValues} stay empty: the binder reads them from MythicMobs, and a skill
     * choice without {@code Purpose} keeps a provisional inferred purpose until then (M15.2).
     *
     * @throws ConfigurationException with the full path of the first invalid value
     */
    TacticalProfile parse(ConfigurationSection profile, String file, List<ConfigProblem> warnings) {
        ProfileSchema.requireKnown(profile, ProfileSchema.PROFILE, ProfileSchema.VERSION_ONE_PROFILE);
        var mythicId = ConfigurationValues.requiredText(profile, "MythicID");
        requireFixed(profile, "TypeOfMythicID", "MOB");
        requireFixed(profile, "Type", "NORMAL");
        var objective = ConfigurationValues.requiredText(profile, "Objective");
        var movement = sections.movement(profile);
        var choices = ConfigurationValues.section(profile, "Choices")
                .map(section -> choices(section, movement, file, warnings)).orElse(Map.of());
        var tactics = tactics(ConfigurationValues.requiredSection(profile, "Tactics"), choices);
        var contexts = ConfigurationValues.section(profile, "Contexts")
                .map(section -> sections.contexts(section, tactics.keySet(), file, warnings)).orElse(List.of());
        var inheritedStance = sections.settingsStance();
        var inheritedTell = sections.settingsTelegraph();
        try {
            return new TacticalProfile(profile.getName(), mythicId, objective, tactics, choices, contexts,
                    movement, sections.health(profile), sections.threat(profile),
                    ConfigurationValues.textList(profile, "BaseAI"), sections.timing(profile, file, warnings),
                    ParticipantsParser.profile(profile, participants, file, warnings),
                    ProfileDifficultyParser.parse(profile),
                    ProfilePostureParser.stances(profile, inheritedStance, contexts, file, warnings),
                    ProfilePostureParser.telegraph(profile, inheritedTell, file),
                    ProfilePostureParser.poise(profile), zone(profile, file, warnings),
                    group(profile, choices, tactics, file, warnings), patterns(profile, file, warnings),
                    memory(profile, file, warnings), decision(profile, file, warnings), distillMode(profile, file,
                    warnings));
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(profile.getCurrentPath(), exception.getMessage());
        }
    }

    /**
     * The {@code Decision} section (M29.8). It is validated and kept for the overlays, but this build decides with the
     * global keys, so a value other than {@code inherit} warns that it has no effect (DOC-09).
     */
    private akyrion.thepredict.domain.distill.ProfileDecision decision(ConfigurationSection profile, String file,
                                                                       List<ConfigProblem> warnings) {
        var found = ConfigurationValues.section(profile, "Decision");
        if (found.isEmpty()) {
            return akyrion.thepredict.domain.distill.ProfileDecision.INHERIT;
        }
        var section = found.get();
        ProfileSchema.requireKnown(section, java.util.Set.of("Pruning", "MaxOptions", "CandidateScoring", "Diversity"));
        var inherit = akyrion.thepredict.domain.distill.ProfileDecision.INHERIT;
        var decision = new akyrion.thepredict.domain.distill.ProfileDecision(
                ConfigurationValues.token(section, "Pruning", inherit.pruning()),
                ConfigurationValues.intValue(section, "MaxOptions", inherit.maxOptions()),
                ConfigurationValues.text(section, "CandidateScoring", inherit.scoring()),
                ConfigurationValues.token(section, "Diversity", inherit.diversity()));
        inert(section, "Pruning", !decision.pruning().equals(inherit.pruning()), file, warnings);
        inert(section, "MaxOptions", decision.maxOptions() != inherit.maxOptions(), file, warnings);
        inert(section, "CandidateScoring", !decision.scoring().equals(inherit.scoring()), file, warnings);
        inert(section, "Diversity", !decision.diversity().equals(inherit.diversity()), file, warnings);
        return decision;
    }

    /** Warns once that a profile key this build does not read has no effect (DOC-09). */
    static void inert(ConfigurationSection section, String key, boolean changed, String file,
                      List<ConfigProblem> warnings) {
        if (changed) {
            warnings.add(ConfigProblem.warning(file, ConfigurationValues.path(section, key), InertKeys.NO_EFFECT));
        }
    }

    private java.util.Optional<akyrion.thepredict.domain.distill.DistillMode> distillMode(ConfigurationSection profile,
                                                                                         String file,
                                                                                         List<ConfigProblem> warnings) {
        var found = ConfigurationValues.section(profile, "Distill");
        if (found.isEmpty()) {
            return java.util.Optional.empty();
        }
        var section = found.get();
        ProfileSchema.requireKnown(section, java.util.Set.of("Mode"));
        var requested = ModeKeys.parse(section, "Mode", akyrion.thepredict.domain.distill.DistillMode.OFF,
                akyrion.thepredict.domain.distill.DistillMode::parse);
        var global = globalDistill;
        if (requested.rank() > global.rank()) {
            warnings.add(new ConfigProblem(ConfigProblem.Severity.WARNING, file,
                    ConfigurationValues.path(profile, "Distill.Mode"),
                    "is above distill.mode and uses the minimum"));
        }
        return java.util.Optional.of(requested.lower(global));
    }

    private static Map<ChoiceId, SkillChoice> choices(ConfigurationSection section, MovementSettings movement,
                                                      String file, List<ConfigProblem> warnings) {
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var key : section.getKeys(false)) {
            var id = identifier(section, key, ChoiceId::new, "choice");
            var choice = ConfigurationValues.requiredSection(section, key);
            ProfileSchema.requireKnown(choice, ProfileSchema.CHOICE, ProfileSchema.VERSION_ONE_CHOICE);
            var kind = kind(choice);
            var mythicId = kind == ChoiceKind.MYTHIC_SKILL
                    ? Optional.of(ConfigurationValues.requiredText(choice, "MythicID")) : Optional.<String>empty();
            if (kind == ChoiceKind.VANILLA_MELEE && (choice.contains("MythicID") || choice.contains("TypeOfMythicID"))) {
                throw new ConfigurationException(ConfigurationValues.path(choice, "MythicID"),
                        "must be absent for Kind VANILLA_MELEE");
            }
            requireFixed(choice, "TypeOfMythicID", "SKILL");
            var cooldown = ConfigurationValues.intValue(choice, "CD", ProfileDefaults.EXTRA_COOLDOWN_TICKS);
            if (cooldown < 0) {
                throw new ConfigurationException(ConfigurationValues.path(choice, "CD"), "must be >= 0 ticks, was " + cooldown);
            }
            var requiresTarget = ConfigurationValues.booleanValue(choice, "RequiresTarget", ProfileDefaults.REQUIRES_TARGET);
            var purpose = PurposeParser.purpose(choice, kind, movement, file, warnings);
            var built = purpose.isPresent()
                    ? new SkillChoice(id, kind, mythicId, Set.of(), requiresTarget, cooldown, purpose.get())
                    : SkillChoice.untyped(id, mythicId.orElseThrow(), Set.of(), requiresTarget, cooldown);
            var telegraph = ProfilePostureParser.choice(choice);
            if (telegraph != ChoiceTelegraph.NONE) {
                built = built.withTelegraph(telegraph);
            }
            if (choice.contains("Displacement")) {
                built = built.withDisplacement(displacement(choice));
            }
            if (choice.contains("TargetsAllies")) {
                built = built.withTargetsAllies(ConfigurationValues.booleanValue(choice, "TargetsAllies", false));
            }
            choices.put(id, built);
        }
        return choices;
    }

    private ZoneSettings zone(ConfigurationSection profile, String file, List<ConfigProblem> warnings) {
        var parsed = ProfileZoneParser.parse(profile);
        if (!parsed.above(globalZone)) {
            return parsed;
        }
        warnings.add(new ConfigProblem(ConfigProblem.Severity.WARNING, file,
                ConfigurationValues.path(profile, "Zone.Mode"),
                "is above Zone.Mode and uses the minimum"));
        return new ZoneSettings(Optional.of(globalZone.min(parsed.mode().orElseThrow())), parsed.radius(),
                parsed.facts(), parsed.hazardPush());
    }

    private GroupSettings group(ConfigurationSection profile, Map<ChoiceId, SkillChoice> choices,
                                Map<TacticId, Tactic> tactics, String file, List<ConfigProblem> warnings) {
        var capabilities = RoleCapabilities.from(choices, tactics);
        var parsed = ProfileGroupParser.parse(profile, capabilities);
        ConfigurationValues.section(profile, "Group").ifPresent(section -> inert(section, "PlanQuestion",
                parsed.planQuestion() != GroupSettings.DEFAULT.planQuestion(), file, warnings));
        if (!parsed.above(globalGroup)) {
            return parsed;
        }
        warnings.add(new ConfigProblem(ConfigProblem.Severity.WARNING, file,
                ConfigurationValues.path(profile, "Group.Mode"),
                "is above Group.Mode and uses the minimum"));
        return new GroupSettings(Optional.of(globalGroup.min(parsed.mode().orElseThrow())), parsed.squad(),
                parsed.leaderPriority(), parsed.weight(), parsed.roles(), parsed.onLeaderDeath(),
                parsed.againstEntities(), parsed.questions(), parsed.planQuestion(), parsed.capabilities());
    }

    private PatternProfile patterns(ConfigurationSection profile, String file, List<ConfigProblem> warnings) {
        var found = ConfigurationValues.section(profile, "Patterns");
        if (found.isEmpty()) {
            return PatternProfile.DEFAULT;
        }
        var section = found.get();
        ProfileSchema.requireKnown(section, java.util.Set.of("Mode", "AgainstEntities", "Facts"), java.util.Set.of());
        Optional<PatternMode> mode = Optional.empty();
        if (section.contains("Mode")) {
            mode = Optional.of(ModeKeys.required(section, "Mode", PatternMode.class, PatternMode::parse));
        }
        var bench = ModeKeys.parse(section, "AgainstEntities", PatternProfile.DEFAULT.againstEntities(),
                token -> BenchPolicy.parse(token, "Patterns.AgainstEntities"));
        var facts = ConfigurationValues.booleanValue(section, "Facts", PatternProfile.DEFAULT.facts());
        var parsed = new PatternProfile(mode, bench, facts);
        if (!parsed.above(globalPatterns)) {
            return parsed;
        }
        warnings.add(new ConfigProblem(ConfigProblem.Severity.WARNING, file,
                ConfigurationValues.path(profile, "Patterns.Mode"),
                "is above Patterns.Mode and uses the minimum"));
        return new PatternProfile(Optional.of(globalPatterns.min(mode.orElseThrow())), bench, facts);
    }

    private MemoryProfile memory(ConfigurationSection profile, String file, List<ConfigProblem> warnings) {
        var found = ConfigurationValues.section(profile, "Memory");
        if (found.isEmpty()) {
            return MemoryProfile.DEFAULT;
        }
        var section = found.get();
        ProfileSchema.requireKnown(section, java.util.Set.of("Mode", "Facts"), java.util.Set.of());
        Optional<MemoryMode> mode = Optional.empty();
        if (section.contains("Mode")) {
            mode = Optional.of(ModeKeys.required(section, "Mode", MemoryMode.class, MemoryMode::parse));
        }
        var facts = ConfigurationValues.booleanValue(section, "Facts", MemoryProfile.DEFAULT.facts());
        var parsed = new MemoryProfile(mode, facts);
        if (!parsed.above(globalMemory)) {
            return parsed;
        }
        warnings.add(new ConfigProblem(ConfigProblem.Severity.WARNING, file,
                ConfigurationValues.path(profile, "Memory.Mode"), "is above memory.mode and uses the minimum"));
        return new MemoryProfile(Optional.of(globalMemory.min(mode.orElseThrow())), facts);
    }

    private static DisplacementKind displacement(ConfigurationSection choice) {
        var token = ConfigurationValues.requiredText(choice, "Displacement").toUpperCase(Locale.ROOT);
        try {
            return DisplacementKind.valueOf(token);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(choice, "Displacement"),
                    "must name a displacement");
        }
    }

    private Map<TacticId, Tactic> tactics(ConfigurationSection section, Map<ChoiceId, SkillChoice> choices) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        for (var key : section.getKeys(false)) {
            var id = identifier(section, key, TacticId::new, "tactic");
            if (id.isReserved()) {
                throw new ConfigurationException(ConfigurationValues.path(section, key),
                        "uses a reserved tactic name ('none' or the prefix 'debug_')");
            }
            if (key.toLowerCase(Locale.ROOT).startsWith("tell_") || key.toLowerCase(Locale.ROOT).startsWith("stance_")) {
                throw new ConfigurationException(ConfigurationValues.path(section, key),
                        "uses a reserved tactic name (prefix 'tell_' or 'stance_')");
            }
            var tactic = ConfigurationValues.requiredSection(section, key);
            ProfileSchema.requireKnown(tactic, ProfileSchema.TACTIC);
            var movementText = ConfigurationValues.requiredText(tactic, "Movement");
            var movement = Movement.parse(movementText).orElseThrow(() -> new ConfigurationException(
                    ConfigurationValues.path(tactic, "Movement"), "unknown movement '" + movementText + "'"));
            var choice = choiceReference(tactic, choices);
            if (movement == Movement.FLANK && choice.isEmpty()) {
                throw new ConfigurationException(ConfigurationValues.path(tactic, "Choice"), "is required for Movement FLANK");
            }
            var priority = sections.priority(tactic);
            tactics.put(id, new Tactic(id, movement, choice, ConfigurationValues.optionalText(tactic, "Description"),
                    priority, ProfilePostureParser.tacticStance(tactic)));
        }
        if (tactics.isEmpty() || tactics.size() > QuestionSpec.MAX_OPTIONS) {
            throw new ConfigurationException(section.getCurrentPath(), "must declare between 1 and "
                    + QuestionSpec.MAX_OPTIONS + " tactics, had " + tactics.size());
        }
        return tactics;
    }

    private static Optional<ChoiceId> choiceReference(ConfigurationSection tactic, Map<ChoiceId, SkillChoice> choices) {
        var name = ConfigurationValues.optionalText(tactic, "Choice");
        if (name.isEmpty()) {
            return Optional.empty();
        }
        var match = choices.keySet().stream().filter(id -> id.value().equals(name.orElseThrow())).findFirst();
        if (match.isEmpty()) {
            throw new ConfigurationException(ConfigurationValues.path(tactic, "Choice"),
                    "unknown choice '" + name.orElseThrow() + "'");
        }
        return match;
    }

    private static ChoiceKind kind(ConfigurationSection choice) {
        var text = ConfigurationValues.text(choice, "Kind", ChoiceKind.MYTHIC_SKILL.name());
        try {
            return ChoiceKind.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(choice, "Kind"),
                    "must be MYTHIC_SKILL or VANILLA_MELEE, was '" + text + "'");
        }
    }

    private static void requireFixed(ConfigurationSection section, String key, String expected) {
        var value = ConfigurationValues.text(section, key, expected);
        if (!expected.equalsIgnoreCase(value.trim())) {
            throw new ConfigurationException(ConfigurationValues.path(section, key), "only supports " + expected
                    + " in 0.0.1, was '" + value + "'");
        }
    }

    /** Validates a YAML key as an identifier and names the key path on failure. */
    static <T> T identifier(ConfigurationSection section, String key, Function<String, T> factory,
                            String kind) {
        try {
            return factory.apply(key);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, key), "is not a valid " + kind
                    + " name: " + exception.getMessage());
        }
    }
}
