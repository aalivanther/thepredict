package akyrion.thepredict.config;

import akyrion.thepredict.domain.BaseStance;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.ShortWindUp;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.StanceSettings;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.TellAnswer;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reads {@code Stances}, {@code Telegraph} and {@code Poise} from one profile (M23.2, M23.7). Absent sections inherit.
 */
final class ProfilePostureParser {
    static final java.util.Set<String> STANCES = java.util.Set.of("Native", "MinDwellTicks", "HysteresisCycles", "Casts",
            "ByMovement", "EnragedContexts", "EnragedNative", "Describe");
    static final java.util.Set<String> TELEGRAPH = java.util.Set.of("AgainstPlayers", "Commitment", "EntropyCommitment",
            "AgainstEntities", "ShortWindUp", "DefaultRecoveryTicks");
    static final java.util.Set<String> CHOICE_TELEGRAPH = java.util.Set.of("Danger", "Answer", "RecoveryTicks");
    static final java.util.Set<String> POISE = java.util.Set.of("Max", "RegenDelayTicks", "RegenPerSecond",
            "WindUpMultiplier", "BlockedHitPoise", "StaggerTicks", "ImmunityTicks");

    private ProfilePostureParser() {
    }

    static StanceSettings stances(ConfigurationSection profile, StanceSettings inherited, List<ContextRule> contexts,
                                  String file, List<ConfigProblem> warnings) {
        var section = ConfigurationValues.section(profile, "Stances").orElse(null);
        if (section == null) {
            return inherited;
        }
        ProfileSchema.requireKnown(section, STANCES);
        var mode = ModeKeys.parse(section, "Native", inherited.nativeMode(), StanceNativeMode::parse);
        var dwell = ConfigurationValues.intValue(section, "MinDwellTicks", inherited.minDwellTicks());
        var cycles = ConfigurationValues.intValue(section, "HysteresisCycles", inherited.hysteresisCycles());
        var casts = casts(section);
        var movement = byMovement(section);
        var enraged = ConfigurationValues.textList(section, "EnragedContexts");
        var known = contexts.stream().map(rule -> rule.id().value()).collect(java.util.stream.Collectors.toSet());
        for (var name : enraged) {
            if (!known.contains(name)) {
                throw new ConfigurationException(ConfigurationValues.path(section, "EnragedContexts"),
                        "unknown context '" + name + "'");
            }
        }
        var nativeNames = ConfigurationValues.textList(section, "EnragedNative");
        if (!nativeNames.isEmpty() && (mode == StanceNativeMode.READ || mode == StanceNativeMode.OFF)) {
            warnings.add(new ConfigProblem(ConfigProblem.Severity.WARNING, file,
                    ConfigurationValues.path(section, "EnragedNative"),
                    "EnragedNative has no mirror when Native is " + mode.name().toLowerCase(Locale.ROOT)));
        }
        var describe = ConfigurationValues.booleanValue(section, "Describe", inherited.describe());
        ProfileParser.inert(section, "Describe", describe, file, warnings);
        try {
            return new StanceSettings(mode, dwell, cycles, casts, movement, enraged, nativeNames, describe);
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf(section.getCurrentPath(), exception, ProfileSchema::yamlName);
        }
    }

    static TelegraphSettings telegraph(ConfigurationSection profile, TelegraphSettings inherited, String file) {
        var section = ConfigurationValues.section(profile, "Telegraph").orElse(null);
        if (section == null) {
            return inherited;
        }
        ProfileSchema.requireKnown(section, TELEGRAPH);
        var players = section.contains("AgainstPlayers")
                ? flag(section, "AgainstPlayers") : inherited.againstPlayers();
        var entities = section.contains("AgainstEntities")
                ? flag(section, "AgainstEntities") : inherited.againstEntities();
        var commitment = ConfigurationValues.intValue(section, "Commitment", inherited.commitmentPercent());
        var entropy = ConfigurationValues.intValue(section, "EntropyCommitment", inherited.entropyCommitmentPercent());
        var wind = section.contains("ShortWindUp")
                ? ShortWindUp.parse(ConfigurationValues.requiredText(section, "ShortWindUp")) : inherited.shortWindUp();
        var recovery = ConfigurationValues.intValue(section, "DefaultRecoveryTicks", inherited.defaultRecoveryTicks());
        try {
            return new TelegraphSettings(players, entities, inherited.light(), inherited.medium(), inherited.heavy(),
                    inherited.severe(), inherited.blockTicks(), inherited.dodgeTicks(), inherited.backOffTicks(),
                    inherited.breakSightTicks(), inherited.interruptTicks(), inherited.deliveryMarginTicks(),
                    inherited.maxReleasesPerTick(), inherited.whiffRecoveryTicks(), inherited.whiffCooldownTicks(),
                    inherited.resultWindowTicks(), commitment, entropy, wind, recovery);
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf(section.getCurrentPath(), exception, ProfileSchema::yamlName);
        }
    }

    static Optional<PoiseSettings> poise(ConfigurationSection profile) {
        var section = ConfigurationValues.section(profile, "Poise").orElse(null);
        if (section == null) {
            return Optional.empty();
        }
        ProfileSchema.requireKnown(section, POISE);
        var defaults = PoiseSettings.defaults();
        try {
            return Optional.of(new PoiseSettings(ConfigurationValues.decimal(section, "Max", defaults.max()),
                    ConfigurationValues.intValue(section, "RegenDelayTicks", defaults.regenDelayTicks()),
                    ConfigurationValues.decimal(section, "RegenPerSecond", defaults.regenPerSecond()),
                    ConfigurationValues.decimal(section, "WindUpMultiplier", defaults.windUpMultiplier()),
                    ConfigurationValues.decimal(section, "BlockedHitPoise", defaults.blockedHitPoise()),
                    ConfigurationValues.intValue(section, "StaggerTicks", defaults.staggerTicks()),
                    ConfigurationValues.intValue(section, "ImmunityTicks", defaults.immunityTicks())));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf(section.getCurrentPath(), exception, ProfileSchema::yamlName);
        }
    }

    static ChoiceTelegraph choice(ConfigurationSection choice) {
        var section = ConfigurationValues.section(choice, "Telegraph").orElse(null);
        if (section == null) {
            return ChoiceTelegraph.NONE;
        }
        ProfileSchema.requireKnown(section, CHOICE_TELEGRAPH);
        Optional<DangerClass> danger = section.contains("Danger")
                ? Optional.of(enumValue(DangerClass.class, ConfigurationValues.requiredText(section, "Danger"),
                ConfigurationValues.path(section, "Danger"))) : Optional.empty();
        Optional<TellAnswer> answer = section.contains("Answer")
                ? Optional.of(enumValue(TellAnswer.class, ConfigurationValues.requiredText(section, "Answer"),
                ConfigurationValues.path(section, "Answer"))) : Optional.empty();
        OptionalInt recovery = section.contains("RecoveryTicks")
                ? OptionalInt.of(ConfigurationValues.intValue(section, "RecoveryTicks", 0)) : OptionalInt.empty();
        try {
            return new ChoiceTelegraph(danger, answer, recovery);
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf(section.getCurrentPath(), exception, ProfileSchema::yamlName);
        }
    }

    static Optional<BaseStance> tacticStance(ConfigurationSection tactic) {
        if (!tactic.contains("Stance")) {
            return Optional.empty();
        }
        return Optional.of(enumValue(BaseStance.class, ConfigurationValues.requiredText(tactic, "Stance"),
                ConfigurationValues.path(tactic, "Stance")));
    }

    private static Map<BaseStance, List<SkillRole>> casts(ConfigurationSection section) {
        var casts = ConfigurationValues.section(section, "Casts").orElse(null);
        if (casts == null) {
            return Map.of();
        }
        var map = new EnumMap<BaseStance, List<SkillRole>>(BaseStance.class);
        for (var key : casts.getKeys(false)) {
            var base = enumValue(BaseStance.class, key, ConfigurationValues.path(casts, key));
            var roles = new ArrayList<SkillRole>();
            for (var name : ConfigurationValues.textList(casts, key)) {
                roles.add(enumValue(SkillRole.class, name, ConfigurationValues.path(casts, key)));
            }
            map.put(base, List.copyOf(roles));
        }
        return map;
    }

    private static Map<Movement, BaseStance> byMovement(ConfigurationSection section) {
        var source = ConfigurationValues.section(section, "ByMovement").orElse(null);
        if (source == null) {
            return Map.of();
        }
        var map = new EnumMap<Movement, BaseStance>(Movement.class);
        for (var key : source.getKeys(false)) {
            var movement = Movement.parse(key).orElseThrow(() -> new ConfigurationException(
                    ConfigurationValues.path(source, key), "unknown movement '" + key + "'"));
            map.put(movement, enumValue(BaseStance.class, ConfigurationValues.requiredText(source, key),
                    ConfigurationValues.path(source, key)));
        }
        return map;
    }

    private static boolean flag(ConfigurationSection section, String key) {
        var text = ConfigurationValues.token(section, key, "off");
        return switch (text) {
            case "enforce", "on" -> true;
            case "off" -> false;
            default -> throw new ConfigurationException(ConfigurationValues.path(section, key),
                    "must be enforce or off, was " + text);
        };
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String text, String path) {
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ConfigurationException(path, "unknown " + type.getSimpleName() + " '" + text + "'");
        }
    }
}
