package akyrion.thepredict.config;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The {@code memory} and {@code learning} sections (M27). Defaults live in {@link MemorySettings#DEFAULT}.
 * {@code mysql} is reserved and refused (errata E27-9 applied).
 */
public final class MemoryConfiguration {
    /** Message for the reserved backend, in English like every other message and without the value (CAR-13). */
    public static final String MYSQL_UNAVAILABLE = "is reserved and not available in this release";

    private MemoryConfiguration() {
    }

    /**
     * Reads the global memory limits. An absent section is the delivered default.
     *
     * @param configuration the master file
     * @return the limits
     */
    public static MemorySettings memory(ConfigurationSection configuration) {
        var learning = learning(configuration);
        var found = ConfigurationValues.section(configuration, "memory");
        if (found.isEmpty()) {
            return withLearning(MemorySettings.DEFAULT, learning.allowUnverified());
        }
        var section = found.get();
        var defaults = MemorySettings.DEFAULT;
        var backend = ConfigurationValues.text(section, "backend", defaults.backend());
        if (backend.equals("mysql")) {
            throw new ConfigurationException("memory.backend", MYSQL_UNAVAILABLE);
        }
        if (!backend.equals("sqlite")) {
            throw new ConfigurationException("memory.backend", "must be sqlite");
        }
        try {
            return new MemorySettings(ModeKeys.parse(section, "mode", defaults.mode(), MemoryMode::parse),
                    ConfigurationValues.decimal(section, "fightDecay", defaults.fightDecay()),
                    ConfigurationValues.intValue(section, "halfLifeDays", defaults.halfLifeDays()),
                    ConfigurationValues.intValue(section, "crossFightH", defaults.crossFightH()),
                    ConfigurationValues.decimal(section, "styleBreakFactor", defaults.styleBreakFactor()),
                    ConfigurationValues.intValue(section, "seedMaxTrials", defaults.seedMaxTrials()),
                    ConfigurationValues.intValue(section, "minFightTrials", defaults.minFightTrials()),
                    ConfigurationValues.intValue(section, "rungPriorFights", defaults.rungPriorFights()),
                    ConfigurationValues.intValue(section, "maxRungOffset", defaults.maxRungOffset()),
                    ConfigurationValues.decimal(section, "minDamageShareRatio", defaults.minDamageShareRatio()),
                    ConfigurationValues.intValue(section, "maxProfilesPerPlayer", defaults.maxProfilesPerPlayer()),
                    ConfigurationValues.intValue(section, "maxFactsPerRequest", defaults.maxFactsPerRequest()),
                    ConfigurationValues.intValue(section, "factMaxAgeDays", defaults.factMaxAgeDays()),
                    ConfigurationValues.intValue(section, "cacheSubjects", defaults.cacheSubjects()),
                    ConfigurationValues.intValue(section, "maxHintsPerTick", defaults.maxHintsPerTick()),
                    ConfigurationValues.intValue(section, "loadTimeoutSeconds", defaults.loadTimeoutSeconds()),
                    ConfigurationValues.intValue(section, "refreshSeconds", defaults.refreshSeconds()),
                    ConfigurationValues.intValue(section, "maxPendingDigestsPerSubject",
                            defaults.maxPendingDigestsPerSubject()),
                    ConfigurationValues.intValue(section, "maxPendingSubjects", defaults.maxPendingSubjects()),
                    ConfigurationValues.intValue(section, "maxSubjectsPerBatch", defaults.maxSubjectsPerBatch()),
                    ConfigurationValues.intValue(section, "retentionDays", defaults.retentionDays()),
                    ConfigurationValues.intValue(section, "maxRows", defaults.maxRows()),
                    learning.allowUnverified(), backend);
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("memory", exception);
        }
    }

    /**
     * Reads {@code learning.allowUnverified}. An absent section is false.
     *
     * @param configuration the master file
     * @return the setting
     */
    public static LearningSettings learning(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "learning");
        if (found.isEmpty()) {
            return LearningSettings.DEFAULT;
        }
        return new LearningSettings(ConfigurationValues.booleanValue(found.get(), "allowUnverified",
                LearningSettings.DEFAULT.allowUnverified()));
    }

    private static MemorySettings withLearning(MemorySettings defaults, boolean allowUnverified) {
        return new MemorySettings(defaults.mode(), defaults.fightDecay(), defaults.halfLifeDays(),
                defaults.crossFightH(), defaults.styleBreakFactor(), defaults.seedMaxTrials(),
                defaults.minFightTrials(), defaults.rungPriorFights(), defaults.maxRungOffset(),
                defaults.minDamageShareRatio(), defaults.maxProfilesPerPlayer(), defaults.maxFactsPerRequest(),
                defaults.factMaxAgeDays(), defaults.cacheSubjects(), defaults.maxHintsPerTick(),
                defaults.loadTimeoutSeconds(), defaults.refreshSeconds(), defaults.maxPendingDigestsPerSubject(),
                defaults.maxPendingSubjects(), defaults.maxSubjectsPerBatch(), defaults.retentionDays(),
                defaults.maxRows(), allowUnverified, defaults.backend());
    }
}
