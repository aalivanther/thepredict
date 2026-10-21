package akyrion.thepredict.config;

import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.learn.CalibratedMinimum;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import akyrion.thepredict.domain.learn.ExploreSettings;
import akyrion.thepredict.domain.learn.TrainingSource;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;

/**
 * Reads {@code learning.*} and the two decision keys of M28. An unknown exploration policy or {@code groupPlans: on}
 * is an error with file and path (errata E28-8 and E28-12 applied). Defaults come from {@link LearningSettings}.
 */
public final class LearningConfiguration {
    private LearningConfiguration() {
    }

    /**
     * Reads the learning section. Absent is {@link LearningSettings#DEFAULT}.
     *
     * @param configuration the master file
     * @return the settings
     */
    public static LearningSettings learning(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "learning");
        if (found.isEmpty()) {
            return LearningSettings.DEFAULT;
        }
        var section = found.get();
        var defaults = LearningSettings.DEFAULT;
        try {
            return new LearningSettings(
                    ConfigurationValues.booleanValue(section, "allowUnverified", defaults.allowUnverified()),
                    ConfigurationValues.booleanValue(section, "collect", defaults.collect()),
                    scope(section, defaults.scope()),
                    base(section, defaults.baseScoring()),
                    sources(section),
                    ConfigurationValues.intValue(section, "contextVersion", defaults.contextVersion()),
                    ConfigurationValues.intValue(section, "maxContextKeysPerProfile", defaults.maxContextKeysPerProfile()),
                    ConfigurationValues.intValue(section, "maxOpenWindowsPerSession", defaults.maxOpenWindowsPerSession()),
                    ConfigurationValues.decimal(section, "creditShare", defaults.creditShare()),
                    ConfigurationValues.decimal(section, "groupCreditShare", defaults.groupCreditShare()),
                    ConfigurationValues.decimal(section, "creditDecayPerWindow", defaults.creditDecay()),
                    ConfigurationValues.intValue(section, "minWindowsForValue", defaults.minWindowsForValue()),
                    ConfigurationValues.intValue(section, "halfLifeDays", defaults.halfLifeDays()),
                    ConfigurationValues.intValue(section, "weeksKept", defaults.weeksKept()),
                    ConfigurationValues.intValue(section, "maxEntries", defaults.maxEntries()),
                    ConfigurationValues.intValue(section, "minValueSpreadMillis", defaults.minValueSpreadMillis()),
                    ConfigurationValues.intValue(section, "buildChunkRows", defaults.buildChunkRows()),
                    ConfigurationValues.booleanValue(section, "exploreInDuels", defaults.exploreInDuels()),
                    exploration(section, defaults.exploration()),
                    nestedInt(section, "calibration", "minDecisions", defaults.calibrationMinDecisions()),
                    nestedInt(section, "calibration", "priorStrength", defaults.priorStrength()),
                    nestedInt(section, "calibration", "driftReferenceWindows", defaults.driftReferenceWindows()),
                    voiMode(section, defaults.voiSkip()),
                    nestedInt(section, "voi", "minWindows", defaults.voiMinWindows()),
                    nestedDecimal(section, "voi", "minAgreement", defaults.voiMinAgreement()),
                    nestedDecimal(section, "voi", "maxExpectedGain", defaults.voiMaxExpectedGain()),
                    nestedInt(section, "voi", "maxConsecutiveSkips", defaults.voiMaxConsecutiveSkips()),
                    hint(section, defaults.jevHint()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("learning", exception);
        }
    }

    /**
     * Reads {@code decision.confidenceGate}.
     *
     * @param configuration the master file
     * @return the gate
     */
    public static ConfidenceGate gate(ConfigurationSection configuration) {
        var raw = ConfigurationValues.text(configuration, "decision.confidenceGate", "raw");
        if ("raw".equals(raw)) {
            return ConfidenceGate.RAW;
        }
        if ("calibrated".equals(raw)) {
            return ConfidenceGate.CALIBRATED;
        }
        throw new ConfigurationException("decision.confidenceGate", "must be raw or calibrated");
    }

    /**
     * Reads {@code decision.minimumCalibratedConfidence}.
     *
     * @param configuration the master file
     * @return parity or an explicit probability
     */
    public static CalibratedMinimum minimum(ConfigurationSection configuration) {
        if (!configuration.contains("decision.minimumCalibratedConfidence")) {
            return CalibratedMinimum.PARITY;
        }
        var raw = configuration.get("decision.minimumCalibratedConfidence");
        if (raw instanceof String text && "parity".equals(text.trim())) {
            return CalibratedMinimum.PARITY;
        }
        if (raw instanceof Number number) {
            return new CalibratedMinimum(false, SettingsChecks.requireRange(number.doubleValue(), 0.50, 0.99,
                    "decision.minimumCalibratedConfidence"));
        }
        throw new ConfigurationException("decision.minimumCalibratedConfidence", "must be parity or a number");
    }

    private static String scope(ConfigurationSection section, String fallback) {
        return ConfigurationValues.text(section, "scope", fallback);
    }

    private static CandidateScoring base(ConfigurationSection section, CandidateScoring fallback) {
        var raw = ConfigurationValues.text(section, "baseScoring", fallback.name().toLowerCase(java.util.Locale.ROOT));
        if ("priority".equals(raw)) {
            return CandidateScoring.PRIORITY;
        }
        if ("utility".equals(raw)) {
            return CandidateScoring.UTILITY;
        }
        throw new ConfigurationException(ConfigurationValues.path(section, "baseScoring"), "must be priority or utility");
    }

    private static java.util.List<TrainingSource> sources(ConfigurationSection section) {
        var tokens = ConfigurationValues.textList(section, "trainingSources");
        if (tokens.isEmpty()) {
            return TrainingSource.DEFAULTS;
        }
        var parsed = new ArrayList<TrainingSource>();
        for (var token : tokens) {
            try {
                parsed.add(TrainingSource.parse(token));
            } catch (IllegalArgumentException exception) {
                throw new ConfigurationException(ConfigurationValues.path(section, "trainingSources"),
                        exception.getMessage());
            }
        }
        return parsed;
    }

    private static ExploreSettings exploration(ConfigurationSection section, ExploreSettings defaults) {
        var nested = ConfigurationValues.section(section, "exploration");
        if (nested.isEmpty()) {
            return defaults;
        }
        var policy = ConfigurationValues.text(nested.get(), "policy", "epsilon");
        if (!"epsilon".equals(policy)) {
            throw new ConfigurationException(ConfigurationValues.path(nested.get(), "policy"),
                    "must be epsilon");
        }
        return new ExploreSettings(ConfigurationValues.decimal(nested.get(), "epsilon", defaults.epsilon()),
                ConfigurationValues.decimal(nested.get(), "maxShare", defaults.maxShare()));
    }

    private static String voiMode(ConfigurationSection section, String fallback) {
        groupPlans(section);
        return ConfigurationValues.token(section, "voiSkip", fallback);
    }

    private static void groupPlans(ConfigurationSection section) {
        var raw = ConfigurationValues.token(section, "groupPlans", "off");
        if (!"off".equals(raw)) {
            throw new ConfigurationException(ConfigurationValues.path(section, "groupPlans"), "must be off");
        }
    }

    private static String hint(ConfigurationSection section, String fallback) {
        return ConfigurationValues.token(section, "jevHint", fallback);
    }

    private static int nestedInt(ConfigurationSection section, String name, String key, int fallback) {
        var nested = ConfigurationValues.section(section, name);
        return nested.isEmpty() ? fallback : ConfigurationValues.intValue(nested.get(), key, fallback);
    }

    private static double nestedDecimal(ConfigurationSection section, String name, String key, double fallback) {
        var nested = ConfigurationValues.section(section, name);
        return nested.isEmpty() ? fallback : ConfigurationValues.decimal(nested.get(), key, fallback);
    }
}
