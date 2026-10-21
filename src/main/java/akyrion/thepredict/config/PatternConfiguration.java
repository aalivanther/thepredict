package akyrion.thepredict.config;

import akyrion.thepredict.domain.AirSwingPolicy;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The {@code patterns} section of {@code config.yml} (M26). Every default lives in {@link PatternSettings#DEFAULT}.
 */
public final class PatternConfiguration {
    private PatternConfiguration() {
    }

    /**
     * Reads the global pattern limits. An absent section is the delivered default.
     *
     * @param configuration the master file
     * @return the limits
     */
    public static PatternSettings patterns(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "patterns");
        if (found.isEmpty()) {
            return PatternSettings.DEFAULT;
        }
        var section = found.get();
        var defaults = PatternSettings.DEFAULT;
        try {
            return new PatternSettings(ModeKeys.parse(section, "mode", defaults.mode(), PatternMode::parse),
                    ConfigurationValues.intValue(section, "hearingBlocks", defaults.hearingBlocks()),
                    ConfigurationValues.intValue(section, "sampleTicks", defaults.sampleTicks()),
                    ConfigurationValues.intValue(section, "maxWatchedPerEncounter", defaults.maxWatchedPerEncounter()),
                    ConfigurationValues.intValue(section, "maxReadsPerTick", defaults.maxReadsPerTick()),
                    ConfigurationValues.decimal(section, "strafeBlocks", defaults.strafeBlocks()),
                    ModeKeys.parse(section, "airSwings", defaults.airSwings(), AirSwingPolicy::parse),
                    ConfigurationValues.intValue(section, "mergeTicks", defaults.mergeTicks()),
                    ConfigurationValues.intValue(section, "idleTicks", defaults.idleTicks()),
                    ConfigurationValues.intValue(section, "maxTokensPerSecond", defaults.maxTokensPerSecond()),
                    ConfigurationValues.intValue(section, "order2Contexts", defaults.order2Contexts()),
                    ConfigurationValues.decimal(section, "forgetting", defaults.forgetting()),
                    ConfigurationValues.decimal(section, "evidenceForgetting", defaults.evidenceForgetting()),
                    ConfigurationValues.decimal(section, "confidenceQuantile", defaults.confidenceQuantile()),
                    ConfigurationValues.intValue(section, "minTrials", defaults.minTrials()),
                    ConfigurationValues.intValue(section, "persistUpdates", defaults.persistUpdates()),
                    ConfigurationValues.decimal(section, "exitMargin", defaults.exitMargin()),
                    ConfigurationValues.decimal(section, "changepointH", defaults.changepointH()),
                    ConfigurationValues.decimal(section, "forecastMinAccuracy", defaults.forecastMinAccuracy()),
                    ConfigurationValues.intValue(section, "histogramBinTicks", defaults.histogramBinTicks()),
                    ConfigurationValues.intValue(section, "maxMicrosPerTick", defaults.maxMicrosPerTick()),
                    ConfigurationValues.decimal(section, "maxExploitShare", defaults.maxExploitShare()),
                    ConfigurationValues.intValue(section, "maxCounterDelayTicks", defaults.maxCounterDelayTicks()),
                    ConfigurationValues.intValue(section, "exploitWindowTicks", defaults.exploitWindowTicks()),
                    ConfigurationValues.intValue(section, "restAfterSuccesses", defaults.restAfterSuccesses()),
                    ConfigurationValues.intValue(section, "restTicks", defaults.restTicks()),
                    ConfigurationValues.intValue(section, "maxExploitedPatterns", defaults.maxExploitedPatterns()),
                    ConfigurationValues.intValue(section, "factOnTicks", defaults.factOnTicks()),
                    ConfigurationValues.intValue(section, "factOffTicks", defaults.factOffTicks()),
                    ConfigurationValues.intValue(section, "maxFactsPerParticipant", defaults.maxFactsPerParticipant()),
                    ConfigurationValues.intValue(section, "maxFactsPerRequest", defaults.maxFactsPerRequest()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("patterns", exception);
        }
    }
}
