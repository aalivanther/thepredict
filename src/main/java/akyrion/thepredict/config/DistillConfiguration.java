package akyrion.thepredict.config;

import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;

/**
 * Reads {@code distill.*}. Absent is {@link DistillSettings#DEFAULT}. Unknown tokens name the path.
 */
public final class DistillConfiguration {
    private DistillConfiguration() {
    }

    /**
     * Reads the section.
     *
     * @param configuration the master file
     * @return the settings
     */
    public static DistillSettings distill(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "distill");
        if (found.isEmpty()) {
            return DistillSettings.DEFAULT;
        }
        var section = found.get();
        var defaults = DistillSettings.DEFAULT;
        try {
            return new DistillSettings(mode(section, defaults.mode()),
                    ConfigurationValues.text(section, "scope", defaults.scope()),
                    ConfigurationValues.booleanValue(section, "collect", defaults.collect()),
                    ConfigurationValues.intValue(section, "minCellOffers", defaults.minCellOffers()),
                    ConfigurationValues.intValue(section, "maxTreeDepth", defaults.maxTreeDepth()),
                    ConfigurationValues.intValue(section, "maxLeaves", defaults.maxLeaves()),
                    ConfigurationValues.intValue(section, "minLeafDecisions", defaults.minLeafDecisions()),
                    ConfigurationValues.intValue(section, "overrideZ", defaults.overrideZ()),
                    ConfigurationValues.intValue(section, "minCellDecisions", defaults.minCellDecisions()),
                    ConfigurationValues.decimal(section, "nonInferiorityMargin", defaults.nonInferiorityMargin()),
                    ConfigurationValues.intValue(section, "coverageRefreshMinutes", defaults.coverageRefreshMinutes()),
                    ConfigurationValues.intValue(section, "coverageMaxAgeMinutes", defaults.coverageMaxAgeMinutes()),
                    ConfigurationValues.intValue(section, "auditEvery", defaults.auditEvery()),
                    ConfigurationValues.intValue(section, "assistMinDelegable", defaults.assistMinDelegable()),
                    jevForOod(section, defaults.jevForOod()),
                    nestedInt(section, "regime", "openingSeconds", defaults.openingSeconds()),
                    nestedInt(section, "regime", "dwellTicks", defaults.dwellTicks()),
                    nestedInt(section, "regime", "maxSwitchesPerEncounter", defaults.maxSwitchesPerEncounter()),
                    nestedDecimal(section, "regime", "minGain", defaults.minGain()),
                    nestedInt(section, "regime", "minDecisions", defaults.minDecisions()),
                    extras(section),
                    ConfigurationValues.intValue(section, "maxRegimesPerProfile", defaults.maxRegimesPerProfile()),
                    ConfigurationValues.intValue(section, "maxArtifactBytes", defaults.maxArtifactBytes()),
                    ConfigurationValues.intValue(section, "weeksKept", defaults.weeksKept()),
                    nestedInt(section, "drift", "cusumH", defaults.cusumH()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("distill", exception);
        }
    }

    private static DistillMode mode(ConfigurationSection section, DistillMode fallback) {
        if (!section.contains("mode")) {
            return fallback;
        }
        try {
            return DistillMode.parse(ConfigurationValues.token(section, "mode", "off"));
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, "mode"),
                    "must be " + ModeNames.of(DistillMode.class));
        }
    }

    private static boolean jevForOod(ConfigurationSection section, boolean fallback) {
        var nested = ConfigurationValues.section(section, "autonomous");
        if (nested.isEmpty() || !nested.get().contains("jevForOod")) {
            return fallback;
        }
        return ConfigurationValues.booleanValue(nested.get(), "jevForOod", fallback);
    }

    private static int nestedInt(ConfigurationSection section, String name, String key, int fallback) {
        var nested = ConfigurationValues.section(section, name);
        return nested.isEmpty() ? fallback : ConfigurationValues.intValue(nested.get(), key, fallback);
    }

    private static double nestedDecimal(ConfigurationSection section, String name, String key, double fallback) {
        var nested = ConfigurationValues.section(section, name);
        return nested.isEmpty() ? fallback : ConfigurationValues.decimal(nested.get(), key, fallback);
    }

    private static java.util.List<String> extras(ConfigurationSection section) {
        var nested = ConfigurationValues.section(section, "regime");
        if (nested.isEmpty() || !nested.get().contains("extraDimensions")) {
            return DistillSettings.DEFAULT.extraDimensions();
        }
        var raw = ConfigurationValues.textList(nested.get(), "extraDimensions");
        return new ArrayList<>(raw);
    }
}
