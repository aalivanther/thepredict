package akyrion.thepredict.config;

import akyrion.thepredict.domain.distill.AutoconfigSettings;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads {@code autoconfig.*}. {@code autoApply} accepts only {@code off}.
 */
public final class AutoconfigConfiguration {
    private AutoconfigConfiguration() {
    }

    /**
     * Reads the section. Absent is {@link AutoconfigSettings#DEFAULT}.
     *
     * @param configuration the master file
     * @return the settings
     */
    public static AutoconfigSettings autoconfig(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "autoconfig");
        if (found.isEmpty()) {
            return AutoconfigSettings.DEFAULT;
        }
        var section = found.get();
        var defaults = AutoconfigSettings.DEFAULT;
        try {
            return new AutoconfigSettings(
                    ConfigurationValues.intValue(section, "initialConfigs", defaults.initialConfigs()),
                    ConfigurationValues.intValue(section, "initialPairs", defaults.initialPairs()),
                    ConfigurationValues.intValue(section, "eta", defaults.eta()),
                    ConfigurationValues.intValue(section, "maxPairs", defaults.maxPairs()),
                    ConfigurationValues.intValue(section, "maxMinutes", defaults.maxMinutes()),
                    ConfigurationValues.token(section, "autoApply", defaults.autoApply()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("autoconfig", exception);
        }
    }
}
