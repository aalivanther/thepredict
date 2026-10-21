package akyrion.thepredict.config;

import akyrion.thepredict.domain.DifficultyMode;
import akyrion.thepredict.domain.DifficultySettings;
import akyrion.thepredict.domain.NativeLevelMode;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The {@code Difficulty} section of a profile (M22.11). An absent {@code TargetSeconds} is not an error (errata E22-3).
 */
final class ProfileDifficultyParser {
    private ProfileDifficultyParser() {
    }

    /**
     * Reads the section. An absent section inherits everything.
     *
     * @param profile the profile
     * @return the settings
     */
    static DifficultySettings parse(ConfigurationSection profile) {
        var section = profile.getConfigurationSection("Difficulty");
        if (section == null) {
            if (profile.contains("Difficulty")) {
                throw new ConfigurationException(ConfigurationValues.path(profile, "Difficulty"), "must be a section");
            }
            return DifficultySettings.DEFAULT;
        }
        ProfileSchema.requireKnown(section, ProfileSchema.DIFFICULTY);
        var defaults = DifficultySettings.DEFAULT;
        try {
            return new DifficultySettings(mode(section), target(section), tolerance(section),
                    ConfigurationValues.intValue(section, "MinRung", defaults.minRung()),
                    ConfigurationValues.intValue(section, "MaxRung", defaults.maxRung()), start(section),
                    ConfigurationValues.booleanValue(section, "InFight", defaults.inFight()),
                    ConfigurationValues.booleanValue(section, "Learn", defaults.learn()), nativeLevel(section));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf(section.getCurrentPath(), exception, ProfileSchema::yamlName);
        }
    }

    private static Optional<DifficultyMode> mode(ConfigurationSection section) {
        if (!section.contains("Mode")) {
            return Optional.empty();
        }
        var text = ConfigurationValues.requiredText(section, "Mode").toLowerCase(java.util.Locale.ROOT);
        try {
            return Optional.of(DifficultyMode.parse(text));
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, "Mode"), "must be auto, bench or player");
        }
    }

    private static OptionalInt target(ConfigurationSection section) {
        if (!section.contains("TargetSeconds")) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ConfigurationValues.intValue(section, "TargetSeconds", 0));
    }

    private static OptionalDouble tolerance(ConfigurationSection section) {
        if (!section.contains("Tolerance")) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(ConfigurationValues.decimal(section, "Tolerance", 0));
    }

    private static OptionalInt start(ConfigurationSection section) {
        if (!section.contains("StartRung")) {
            return OptionalInt.empty();
        }
        var value = section.get("StartRung");
        if (value instanceof String text && text.equalsIgnoreCase("auto")) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ConfigurationValues.intValue(section, "StartRung", 0));
    }

    private static NativeLevelMode nativeLevel(ConfigurationSection section) {
        if (!section.contains("NativeLevel")) {
            return NativeLevelMode.DEFAULT;
        }
        var text = ConfigurationValues.token(section, "NativeLevel", "").trim().toLowerCase(java.util.Locale.ROOT);
        try {
            return NativeLevelMode.parse(text);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, "NativeLevel"), "must be off or spawn");
        }
    }
}
