package akyrion.thepredict.config;

import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The {@code group} section of {@code config.yml} (M25). Every default lives in {@link GroupCaps#DEFAULT}.
 */
public final class GroupConfiguration {
    private GroupConfiguration() {
    }

    /**
     * Reads the global group limits. An absent section is the delivered default.
     *
     * @param configuration the master file
     * @return the limits
     */
    public static GroupCaps group(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "group");
        if (found.isEmpty()) {
            return GroupCaps.DEFAULT;
        }
        var section = found.get();
        var defaults = GroupCaps.DEFAULT;
        try {
            return new GroupCaps(ModeKeys.parse(section, "mode", defaults.mode(), GroupMode::parse),
                    ConfigurationValues.intValue(section, "maxSquads", defaults.maxSquads()),
                    ConfigurationValues.intValue(section, "brokenTicks", defaults.brokenTicks()),
                    ConfigurationValues.intValue(section, "arbitrationPeriodTicks", defaults.arbitrationPeriodTicks()),
                    ConfigurationValues.intValue(section, "replanPeriodTicks", defaults.replanPeriodTicks()),
                    ConfigurationValues.intValue(section, "maxReplansPerTick", defaults.maxReplansPerTick()),
                    ConfigurationValues.intValue(section, "maxMicrosPerTick", defaults.maxMicrosPerTick()),
                    ConfigurationValues.intValue(section, "maxMeleeAttackersPerPlayer",
                            defaults.maxMeleeAttackersPerPlayer()),
                    ConfigurationValues.intValue(section, "maxRangedAttackersPerPlayer",
                            defaults.maxRangedAttackersPerPlayer()),
                    ConfigurationValues.intValue(section, "minImpactGapTicks", defaults.minImpactGapTicks()),
                    ConfigurationValues.intValue(section, "tokenIdleTimeoutTicks", defaults.tokenIdleTimeoutTicks()),
                    ConfigurationValues.intValue(section, "stealMarginMillis", defaults.stealMarginMillis()),
                    ConfigurationValues.intValue(section, "idleWarnTicks", defaults.idleWarnTicks()),
                    ConfigurationValues.intValue(section, "roleSwitchMarginMillis", defaults.roleSwitchMarginMillis()),
                    ConfigurationValues.intValue(section, "exitSectorDegrees", defaults.exitSectorDegrees()),
                    ConfigurationValues.intValue(section, "lkpForgetTicks", defaults.lkpForgetTicks()),
                    ConfigurationValues.intValue(section, "maxMediumTellsPerPlayer",
                            defaults.maxMediumTellsPerPlayer()),
                    ConfigurationValues.intValue(section, "bigImpactSpacingTicks", defaults.bigImpactSpacingTicks()),
                    ConfigurationValues.intValue(section, "leaderReserveTicks", defaults.leaderReserveTicks()),
                    ConfigurationValues.intValue(section, "projectileFlightMarginTicks",
                            defaults.projectileFlightMarginTicks()),
                    ConfigurationValues.intValue(section, "maxPlanCandidates", defaults.maxPlanCandidates()),
                    ConfigurationValues.intValue(section, "minPlanTicks", defaults.minPlanTicks()),
                    ConfigurationValues.intValue(section, "planHorizonTicks", defaults.planHorizonTicks()),
                    ConfigurationValues.intValue(section, "assignmentThreatMargin", defaults.assignmentThreatMargin()),
                    ConfigurationValues.intValue(section, "factOnTicks", defaults.factOnTicks()),
                    ConfigurationValues.intValue(section, "factOffTicks", defaults.factOffTicks()),
                    ConfigurationValues.intValue(section, "maxFacts", defaults.maxFacts()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("group", exception);
        }
    }
}
