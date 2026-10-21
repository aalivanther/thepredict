package akyrion.thepredict.config;

import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.StanceSettings;
import akyrion.thepredict.domain.TelegraphSettings;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;

/**
 * The {@code telegraph} and {@code stance} sections of {@code config.yml} (M23.11). Commitment stays on the profile.
 * A {@code poise} key is not accepted here. Every default lives in {@link TelegraphSettings#DEFAULT} and
 * {@link StanceSettings#DEFAULT}; a range failure of the records names the YAML leaf (C13).
 */
public final class TelegraphConfiguration {
    /** Record fields of {@link TelegraphSettings} that live under a nested key of the section. */
    private static final Map<String, String> TELEGRAPH_KEYS = Map.of("light", "minTicks.light",
            "medium", "minTicks.medium", "heavy", "minTicks.heavy", "severe", "minTicks.severe",
            "blockTicks", "answerTicks.block", "dodgeTicks", "answerTicks.dodge", "backOffTicks", "answerTicks.backOff",
            "breakSightTicks", "answerTicks.breakSight", "interruptTicks", "answerTicks.interrupt");

    private TelegraphConfiguration() {
    }

    /**
     * Reads the global tell lengths.
     *
     * @param configuration the master file
     * @return the settings
     */
    public static TelegraphSettings telegraph(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "telegraph");
        if (found.isEmpty()) {
            return TelegraphSettings.DEFAULT;
        }
        var section = found.get();
        var defaults = TelegraphSettings.DEFAULT;
        var minima = ConfigurationValues.requiredSection(section, "minTicks");
        var answers = ConfigurationValues.requiredSection(section, "answerTicks");
        try {
            return new TelegraphSettings(mode(section, "againstPlayers", defaults.againstPlayers()),
                    mode(section, "againstEntities", defaults.againstEntities()),
                    ConfigurationValues.intValue(minima, "light", defaults.light()),
                    ConfigurationValues.intValue(minima, "medium", defaults.medium()),
                    ConfigurationValues.intValue(minima, "heavy", defaults.heavy()),
                    ConfigurationValues.intValue(minima, "severe", defaults.severe()),
                    ConfigurationValues.intValue(answers, "block", defaults.blockTicks()),
                    ConfigurationValues.intValue(answers, "dodge", defaults.dodgeTicks()),
                    ConfigurationValues.intValue(answers, "backOff", defaults.backOffTicks()),
                    ConfigurationValues.intValue(answers, "breakSight", defaults.breakSightTicks()),
                    ConfigurationValues.intValue(answers, "interrupt", defaults.interruptTicks()),
                    ConfigurationValues.intValue(section, "deliveryMarginTicks", defaults.deliveryMarginTicks()),
                    ConfigurationValues.intValue(section, "maxReleasesPerTick", defaults.maxReleasesPerTick()),
                    ConfigurationValues.intValue(section, "whiffRecoveryTicks", defaults.whiffRecoveryTicks()),
                    ConfigurationValues.intValue(section, "whiffCooldownTicks", defaults.whiffCooldownTicks()),
                    ConfigurationValues.intValue(section, "resultWindowTicks", defaults.resultWindowTicks()),
                    defaults.commitmentPercent(), defaults.entropyCommitmentPercent(), defaults.shortWindUp(),
                    defaults.defaultRecoveryTicks());
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("telegraph", exception, field -> TELEGRAPH_KEYS.getOrDefault(field, field));
        }
    }

    /**
     * Reads the global stance timing.
     *
     * @param configuration the master file
     * @return the settings, with empty profile maps
     */
    public static StanceSettings stance(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "stance");
        if (found.isEmpty()) {
            return StanceSettings.DEFAULT;
        }
        var section = found.get();
        var defaults = StanceSettings.DEFAULT;
        try {
            return new StanceSettings(nativeMode(section, defaults.nativeMode()),
                    ConfigurationValues.intValue(section, "minDwellTicks", defaults.minDwellTicks()),
                    ConfigurationValues.intValue(section, "hysteresisCycles", defaults.hysteresisCycles()),
                    defaults.casts(), defaults.byMovement(), defaults.enragedContexts(), defaults.enragedNative(),
                    defaults.describe());
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("stance", exception);
        }
    }

    private static StanceNativeMode nativeMode(ConfigurationSection section, StanceNativeMode fallback) {
        var text = ConfigurationValues.token(section, "nativeMode", fallback.name().toLowerCase(Locale.ROOT));
        try {
            return StanceNativeMode.parse(text);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, "nativeMode"),
                    "must be " + ModeNames.of(StanceNativeMode.class));
        }
    }

    private static boolean mode(ConfigurationSection section, String key, boolean fallback) {
        var text = ConfigurationValues.token(section, key, fallback ? "enforce" : "off");
        return switch (text) {
            case "enforce", "on" -> true;
            case "off" -> false;
            default -> throw new ConfigurationException(ConfigurationValues.path(section, key),
                    "must be enforce or off, was " + text);
        };
    }
}
