package akyrion.thepredict.config;

import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.ZoneMode;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The {@code zone} section of {@code config.yml} (M24.11). Every default lives in {@link ZoneCaps#DEFAULT}.
 * There is no cover-ray key (errata E24-4).
 */
public final class ZoneConfiguration {
    private ZoneConfiguration() {
    }

    /**
     * Reads the global zone limits. An absent section is the delivered default.
     *
     * @param configuration the master file
     * @return the limits
     */
    public static ZoneCaps zone(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "zone");
        if (found.isEmpty()) {
            return ZoneCaps.DEFAULT;
        }
        var section = found.get();
        var defaults = ZoneCaps.DEFAULT;
        try {
            return new ZoneCaps(ModeKeys.parse(section, "mode", defaults.mode(), ZoneMode::parse),
                    ConfigurationValues.intValue(section, "defaultRadiusBlocks", defaults.defaultRadiusBlocks()),
                    ConfigurationValues.intValue(section, "maxRadiusBlocks", defaults.maxRadiusBlocks()),
                    ConfigurationValues.intValue(section, "bandBlocks", defaults.bandBlocks()),
                    ConfigurationValues.intValue(section, "maxZones", defaults.maxZones()),
                    ConfigurationValues.intValue(section, "maxSnapshotsPerTick", defaults.maxSnapshotsPerTick()),
                    ConfigurationValues.intValue(section, "maxConcurrentBuilds", defaults.maxConcurrentBuilds()),
                    ConfigurationValues.intValue(section, "maxDropScanBlocks", defaults.maxDropScanBlocks()),
                    ConfigurationValues.intValue(section, "maxMicrosPerTick", defaults.maxMicrosPerTick()),
                    ConfigurationValues.intValue(section, "maxColumnReadsPerTick", defaults.maxColumnReadsPerTick()),
                    ConfigurationValues.intValue(section, "minColumnRefreshTicks", defaults.minColumnRefreshTicks()),
                    ConfigurationValues.intValue(section, "maxDirtyQueue", defaults.maxDirtyQueue()),
                    ConfigurationValues.intValue(section, "dirtyRebuildThreshold", defaults.dirtyRebuildThreshold()),
                    ConfigurationValues.intValue(section, "verifyIntervalTicks", defaults.verifyIntervalTicks()),
                    ConfigurationValues.intValue(section, "idleDropTicks", defaults.idleDropTicks()),
                    ConfigurationValues.intValue(section, "maxQueriesPerTick", defaults.maxQueriesPerTick()),
                    ConfigurationValues.intValue(section, "knockbackDepthBlocks", defaults.knockbackDepthBlocks()),
                    ConfigurationValues.intValue(section, "strongPushDepthBlocks", defaults.strongPushDepthBlocks()),
                    ConfigurationValues.intValue(section, "maxSafeDropBlocks", defaults.maxSafeDropBlocks()),
                    ConfigurationValues.intValue(section, "factPeriodTicks", defaults.factPeriodTicks()),
                    ConfigurationValues.intValue(section, "factOnTicks", defaults.factOnTicks()),
                    ConfigurationValues.intValue(section, "factOffTicks", defaults.factOffTicks()),
                    ConfigurationValues.intValue(section, "maxFactsPerMob", defaults.maxFactsPerMob()),
                    ConfigurationValues.intValue(section, "pushDeferTicks", defaults.pushDeferTicks()),
                    ConfigurationValues.intValue(section, "pushDeferMaxTicks", defaults.pushDeferMaxTicks()),
                    ConfigurationValues.intValue(section, "pushAttributionTicks", defaults.pushAttributionTicks()),
                    ConfigurationValues.intValue(section, "answerRetryTicks", defaults.answerRetryTicks()),
                    ConfigurationValues.intValue(section, "answerMaxWaitTicks", defaults.answerMaxWaitTicks()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("zone", exception);
        }
    }
}
