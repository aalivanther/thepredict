package akyrion.thepredict.config;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;

/** Parses the contexts and the numeric sections of a schema 2 profile, inheriting the defaults (M03.2, M03.3). */
final class ProfileSectionParser {
    private final PluginSettings settings;

    /** Creates the parser with the inherited defaults. */
    ProfileSectionParser(PluginSettings settings) {
        this.settings = settings;
    }

    /** Global stance section inherited by a profile that omits {@code Stances}. */
    akyrion.thepredict.domain.StanceSettings settingsStance() {
        return settings.stance();
    }

    /** Global tell section inherited by a profile that omits {@code Telegraph}. */
    akyrion.thepredict.domain.TelegraphSettings settingsTelegraph() {
        return settings.telegraph();
    }

    /** Reads {@code Priority} between 0 and 100, defaulting to {@link ProfileDefaults#PRIORITY}. */
    int priority(ConfigurationSection section) {
        var priority = ConfigurationValues.intValue(section, "Priority", ProfileDefaults.PRIORITY);
        if (priority < Tactic.MIN_PRIORITY || priority > Tactic.MAX_PRIORITY) {
            throw new ConfigurationException(ConfigurationValues.path(section, "Priority"), "must be between "
                    + Tactic.MIN_PRIORITY + " and " + Tactic.MAX_PRIORITY + ", was " + priority);
        }
        return priority;
    }

    /** Reads the {@code Contexts} section; equal priorities are a warning because declaration order decides. */
    List<ContextRule> contexts(ConfigurationSection section, Set<TacticId> declared, String file,
                               List<ConfigProblem> warnings) {
        var contexts = new ArrayList<ContextRule>();
        var priorities = new HashMap<Integer, ContextId>();
        for (var key : section.getKeys(false)) {
            var id = ProfileParser.identifier(section, key, ContextId::new, "context");
            var context = ConfigurationValues.requiredSection(section, key);
            ProfileSchema.requireKnown(context, ProfileSchema.CONTEXT);
            var allowed = tactics(context, declared);
            var direct = directTactic(context, declared);
            if (direct.isPresent() && !allowed.contains(direct.orElseThrow())) {
                throw new ConfigurationException(ConfigurationValues.path(context, "DirectTactic"),
                        "must be listed in Tactics");
            }
            var rule = new ContextRule(id, conditions(context, id), priority(context), allowed, direct);
            var earlier = priorities.putIfAbsent(rule.priority(), id);
            if (earlier != null) {
                warnings.add(ConfigProblem.warning(file, ConfigurationValues.path(context, "Priority"),
                        "has the same Priority " + rule.priority() + " as context '" + earlier
                                + "'; the one declared first wins when both are active"));
            }
            contexts.add(rule);
        }
        if (contexts.size() > ProfileSchema.MAX_CONTEXTS) {
            throw new ConfigurationException(section.getCurrentPath(), "must declare at most "
                    + ProfileSchema.MAX_CONTEXTS + " contexts, had " + contexts.size());
        }
        return contexts;
    }

    /** Reads {@code Movement}; absent values inherit {@code profileDefaults}. */
    MovementSettings movement(ConfigurationSection profile) {
        var defaults = settings.profileDefaults();
        var movement = ConfigurationValues.section(profile, "Movement");
        if (movement.isEmpty()) {
            return defaults.movement();
        }
        var section = movement.orElseThrow();
        ProfileSchema.requireKnown(section, ProfileSchema.MOVEMENT);
        requireKnownNested(section, "Band", ProfileSchema.BAND);
        requireKnownNested(section, "Retreat", ProfileSchema.RETREAT);
        requireKnownNested(section, "Approach", ProfileSchema.APPROACH);
        requireKnownNested(section, "Arena", ProfileSchema.ARENA);
        var radius = section.contains("Arena.Radius")
                ? OptionalDouble.of(ConfigurationValues.decimal(section, "Arena.Radius", 0.0)) : OptionalDouble.empty();
        var retreatSpeed = speed(section, "Retreat.Speed", defaults.retreatSpeed());
        var approachSpeed = speed(section, "Approach.Speed", defaults.approachSpeed());
        return domain(section, () -> new MovementSettings(
                ConfigurationValues.decimal(section, "MeleeReach", defaults.meleeReachBlocks()),
                ConfigurationValues.decimal(section, "Band.Inner", defaults.bandInnerBlocks()),
                ConfigurationValues.decimal(section, "Band.Outer", defaults.bandOuterBlocks()),
                ConfigurationValues.decimal(section, "RangedReach", defaults.rangedReachBlocks()),
                ConfigurationValues.decimal(section, "Retreat.Distance", defaults.retreatDistanceBlocks()),
                retreatSpeed, approachSpeed, radius));
    }

    /** Reads {@code Health}; absent values inherit {@code profileDefaults}. */
    HealthThresholds health(ConfigurationSection profile) {
        var defaults = settings.profileDefaults();
        var health = ConfigurationValues.section(profile, "Health");
        health.ifPresent(section -> ProfileSchema.requireKnown(section, ProfileSchema.HEALTH));
        return health.map(section -> domain(section, () -> new HealthThresholds(
                ConfigurationValues.decimal(section, "Critical", defaults.criticalHealthRatio()),
                ConfigurationValues.decimal(section, "Wounded", defaults.woundedHealthRatio()))))
                .orElse(defaults.health());
    }

    /** Reads {@code Threat}; absent weights inherit {@link ProfileDefaults#THREAT}. */
    ThreatWeights threat(ConfigurationSection profile) {
        var threat = ConfigurationValues.section(profile, "Threat");
        if (threat.isEmpty()) {
            return ProfileDefaults.THREAT;
        }
        var section = threat.orElseThrow();
        ProfileSchema.requireKnown(section, ProfileSchema.THREAT);
        var standard = ProfileDefaults.THREAT;
        var maxPerWindow = ConfigurationValues.decimal(section, "MaxPerWindow", standard.maxPerWindow());
        if (!(maxPerWindow > 0.0)) {
            throw new ConfigurationException(ConfigurationValues.path(section, "MaxPerWindow"), "must be > 0, was "
                    + maxPerWindow);
        }
        return domain(section, () -> new ThreatWeights(
                ConfigurationValues.decimal(section, "AssistsOthers", standard.assistsOthers()),
                ConfigurationValues.decimal(section, "RangedHarass", standard.rangedHarass()),
                ConfigurationValues.decimal(section, "BlockedHits", standard.blockedHits()),
                ConfigurationValues.decimal(section, "UnreachableDecayPerSecond", standard.unreachableDecayPerSecond()),
                maxPerWindow));
    }

    /**
     * Reads {@code Timing}; the global heartbeat, intervals and contingencies are never widened by a profile. Absent
     * {@code QuestionDetail} and {@code FitChecks} inherit {@code decision.questionDetail} and {@code decision.fitChecks}
     * (M16.2, M16.5). {@code FitChecks: true} is an error while {@code decision.fitChecksAllowed} is false, and a warning
     * when the detail is not {@code full}, because only {@code full} sends them.
     */
    DecisionTiming timing(ConfigurationSection profile, String file, List<ConfigProblem> warnings) {
        var questions = settings.decision().questions();
        var timing = ConfigurationValues.section(profile, "Timing");
        timing.ifPresent(section -> ProfileSchema.requireKnown(section, ProfileSchema.TIMING));
        var confidence = timing.map(section -> ratio(section, "MinimumConfidence",
                settings.decision().minimumConfidence())).orElse(settings.decision().minimumConfidence());
        var margin = timing.map(section -> ratio(section, "SwitchMargin", settings.decision().tacticSwitchMargin()))
                .orElse(settings.decision().tacticSwitchMargin());
        var detail = timing.map(section -> QuestionSettings.detail(section, "QuestionDetail", questions.questionDetail()))
                .orElse(questions.questionDetail());
        var fitChecks = timing.map(section -> ConfigurationValues.booleanValue(section, "FitChecks",
                questions.fitChecks())).orElse(questions.fitChecks());
        var path = timing.filter(section -> section.contains("FitChecks"))
                .map(section -> ConfigurationValues.path(section, "FitChecks")).orElse("decision.fitChecks");
        if (fitChecks && !questions.fitChecksAllowed()) {
            throw new ConfigurationException(path, "must be false while decision.fitChecksAllowed is false");
        }
        if (fitChecks && detail != QuestionDetail.FULL) {
            warnings.add(ConfigProblem.warning(file, path, "is ignored: fit checks are only sent with QuestionDetail"
                    + " full, and this profile uses " + QuestionSettings.text(detail)));
        }
        var timingOf = settings.timing(confidence, margin, detail, fitChecks);
        var castMode = timing.map(section -> DecisionSettings.mode(section, "CastMode", CastMode.class,
                timingOf.castMode())).orElse(timingOf.castMode());
        var horizon = timing.map(section -> ConfigurationValues.intValue(section, "ReadyHorizon",
                timingOf.readyHorizonTicks())).orElse(timingOf.readyHorizonTicks());
        var cast = timingOf.withCasting(castMode, horizon);
        return layer(profile, file, warnings, timing, cast);
    }

    private DecisionTiming layer(ConfigurationSection profile, String file, List<ConfigProblem> warnings,
                                 Optional<ConfigurationSection> timing, DecisionTiming cast) {
        var global = settings.decision().tickLayerMode();
        var requested = timing.map(section -> DecisionSettings.mode(section, "TickLayer", TickLayerMode.class,
                cast.tickLayer())).orElse(cast.tickLayer());
        var mode = requested.lesser(global);
        if (requested != mode) {
            var path = timing.map(section -> ConfigurationValues.path(section, "TickLayer")).orElse("Timing.TickLayer");
            warnings.add(ConfigProblem.warning(file, path, "is above the global " + global.name().toLowerCase()
                    + "; using " + mode.name().toLowerCase()));
        }
        var scope = timing.map(section -> DecisionSettings.mode(section, "Reactions", ReactionScope.class,
                cast.reactions())).orElse(cast.reactions());
        var delay = timing.map(section -> ConfigurationValues.intValue(section, "ReactionDelay",
                cast.reactionDelayTicks())).orElse(cast.reactionDelayTicks());
        var cadence = timing.map(section -> DecisionSettings.mode(section, "Cadence", DecisionCadence.class,
                cast.cadence())).orElse(cast.cadence());
        var interval = timing.map(section -> ConfigurationValues.intValue(section, "ReactiveInterval",
                cast.reactiveIntervalTicks())).orElse(cast.reactiveIntervalTicks());
        var intentHorizon = cast.intentHorizonTicks();
        if (timing.isPresent() && timing.get().contains("IntentHorizon")) {
            var path = ConfigurationValues.path(timing.get(), "IntentHorizon");
            var asked = ConfigurationValues.intValue(timing.get(), "IntentHorizon", intentHorizon);
            if (cadence == DecisionCadence.STANDARD) {
                warnings.add(ConfigProblem.warning(file, path, "is ignored while Cadence is standard"));
            } else if (asked < 4 || asked > 200 || asked > cast.maximumIntentTicks()) {
                throw new ConfigurationException(path, "must be within [4, 200] and <= maximumIntentTicks ("
                        + cast.maximumIntentTicks() + "), was " + asked);
            } else {
                intentHorizon = asked;
            }
        }
        var ahead = ahead(profile, cast);
        return cast.withLayer(mode, scope, ahead, delay, cadence, interval, intentHorizon);
    }

    private static Set<ReactionBand> ahead(ConfigurationSection profile, DecisionTiming cast) {
        var section = ConfigurationValues.section(profile, "Reactions");
        if (section.isEmpty()) {
            return cast.reactionsAhead();
        }
        ProfileSchema.requireKnown(section.get(), ProfileSchema.REACTIONS);
        if (!section.get().contains("Ahead")) {
            return cast.reactionsAhead();
        }
        var names = ConfigurationValues.textList(section.get(), "Ahead");
        var bands = new LinkedHashSet<ReactionBand>();
        for (var name : names) {
            var text = name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
            ReactionBand found = null;
            for (var band : ReactionBand.values()) {
                if (band.name().equals(text)) {
                    found = band;
                }
            }
            if (found == null) {
                throw new ConfigurationException(ConfigurationValues.path(section.get(), "Ahead"),
                        "must be survival, opportunity or spacing, was '" + name + "'");
            }
            bands.add(found);
        }
        return Set.copyOf(bands);
    }

    private static List<String> conditions(ConfigurationSection context, ContextId id) {
        var lines = ConfigurationValues.textList(context, "Conditions");
        var shortcut = ContextRule.SHORTCUTS.contains(id);
        if (shortcut && context.contains("Conditions")) {
            throw new ConfigurationException(ConfigurationValues.path(context, "Conditions"),
                    "must be absent: '" + id + "' is a shortcut context computed without native conditions");
        }
        if (!shortcut && lines.isEmpty()) {
            throw new ConfigurationException(ConfigurationValues.path(context, "Conditions"),
                    "must list at least one MythicMobs condition line");
        }
        return lines;
    }

    private static Set<TacticId> tactics(ConfigurationSection context, Set<TacticId> declared) {
        var names = ConfigurationValues.textList(context, "Tactics");
        if (names.isEmpty()) {
            throw new ConfigurationException(ConfigurationValues.path(context, "Tactics"), "must list at least one tactic");
        }
        var tactics = new LinkedHashSet<TacticId>();
        for (var name : names) {
            var tactic = declared.stream().filter(id -> id.value().equals(name)).findFirst().orElseThrow(() ->
                    new ConfigurationException(ConfigurationValues.path(context, "Tactics"), "unknown tactic '" + name + "'"));
            if (!tactics.add(tactic)) {
                throw new ConfigurationException(ConfigurationValues.path(context, "Tactics"), "lists '" + name + "' twice");
            }
        }
        return tactics;
    }

    private static Optional<TacticId> directTactic(ConfigurationSection context, Set<TacticId> declared) {
        return ConfigurationValues.optionalText(context, "DirectTactic").map(name -> declared.stream()
                .filter(id -> id.value().equals(name)).findFirst().orElseThrow(() -> new ConfigurationException(
                        ConfigurationValues.path(context, "DirectTactic"), "unknown tactic '" + name + "'")));
    }

    private static void requireKnownNested(ConfigurationSection section, String key, Set<String> allowed) {
        ConfigurationValues.section(section, key).ifPresent(value -> ProfileSchema.requireKnown(value, allowed));
    }

    private static double speed(ConfigurationSection section, String path, double fallback) {
        var speed = ConfigurationValues.decimal(section, path, fallback);
        if (section.contains(path)) {
            SettingsChecks.requireRange(speed, ProfileDefaults.MIN_SPEED, ProfileDefaults.MAX_SPEED,
                    ConfigurationValues.path(section, path));
        }
        return speed;
    }

    private static double ratio(ConfigurationSection section, String key, double fallback) {
        return SettingsChecks.requireRange(ConfigurationValues.decimal(section, key, fallback), 0.0,
                DecisionSettings.MAX_RATIO, ConfigurationValues.path(section, key));
    }

    private static <T> T domain(ConfigurationSection section, Supplier<T> factory) {
        try {
            return factory.get();
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(section.getCurrentPath(), ProfileSchema.yamlNames(exception.getMessage()));
        }
    }
}
