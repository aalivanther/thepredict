package akyrion.thepredict.config;

import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ThreatWeights;
import org.bukkit.configuration.ConfigurationSection;

import java.util.OptionalDouble;

/**
 * The {@code profileDefaults.*} section of {@code config.yml}: values every profile inherits unless it
 * overrides them (precedence: internal defaults, then {@code config.yml}, then the profile; M03.2).
 *
 * @param observationIntervalTicks ticks between cheap probes of a session, 1 to 20
 * @param minimumDecisionIntervalTicks minimum ticks an adopted tactic is kept, 10 to 200
 * @param maximumIntentDurationTicks ticks an intent stays valid, 20 to 400 and at least the minimum interval
 * @param criticalHealthRatio health ratio below which a creature is critical, between 0 and 1 exclusive
 * @param woundedHealthRatio health ratio below which a creature is wounded, above the critical ratio and below 1
 * @param meleeReachBlocks distance up to which the target is in melee, greater than 0
 * @param bandInnerBlocks inner edge of the keep-distance band, greater than {@code meleeReachBlocks}
 * @param bandOuterBlocks outer edge of the keep-distance band, greater than {@code bandInnerBlocks}
 * @param rangedReachBlocks distance up to which the target is in ranged reach, at least {@code bandOuterBlocks}
 * @param retreatDistanceBlocks distance a retreat tries to open, greater than 0
 * @param retreatSpeed speed multiplier while retreating, 0.1 to 3
 * @param approachSpeed speed multiplier while approaching, 0.1 to 3
 */
public record ProfileDefaults(int observationIntervalTicks, int minimumDecisionIntervalTicks,
                              int maximumIntentDurationTicks, double criticalHealthRatio, double woundedHealthRatio,
                              double meleeReachBlocks, double bandInnerBlocks, double bandOuterBlocks,
                              double rangedReachBlocks, double retreatDistanceBlocks, double retreatSpeed,
                              double approachSpeed) {
    /** Priority of a tactic or context that does not declare {@code Priority}. */
    public static final int PRIORITY = 50;
    /** Extra cooldown in ticks of a choice without {@code CD}; the recommended cooldown is the native one. */
    public static final int EXTRA_COOLDOWN_TICKS = 0;
    /** Whether a choice without {@code RequiresTarget} needs a target. */
    public static final boolean REQUIRES_TARGET = true;
    /** Threat weights of a profile without a {@code Threat} section (M11.1). */
    public static final ThreatWeights THREAT = new ThreatWeights(1.5, 1.0, 0.25, 2.0, 20.0);
    /** Slowest accepted speed multiplier. */
    public static final double MIN_SPEED = 0.1;
    /** Fastest accepted speed multiplier. */
    public static final double MAX_SPEED = 3.0;

    /**
     * Validates every value and the order of the distances.
     *
     * @throws ConfigurationException if a value is outside its range, naming its path
     */
    public ProfileDefaults {
        SettingsChecks.requireRange(observationIntervalTicks, 1, 20, "profileDefaults.observationIntervalTicks");
        SettingsChecks.requireRange(minimumDecisionIntervalTicks, 10, 200, "profileDefaults.minimumDecisionIntervalTicks");
        SettingsChecks.requireRange(maximumIntentDurationTicks, 20, 400, "profileDefaults.maximumIntentDurationTicks");
        if (maximumIntentDurationTicks < minimumDecisionIntervalTicks) {
            throw new ConfigurationException("profileDefaults.maximumIntentDurationTicks",
                    "must be >= profileDefaults.minimumDecisionIntervalTicks (" + minimumDecisionIntervalTicks
                            + "), was " + maximumIntentDurationTicks);
        }
        SettingsChecks.requireGreater(criticalHealthRatio, 0.0, "profileDefaults.criticalHealthRatio", "0");
        SettingsChecks.requireGreater(woundedHealthRatio, criticalHealthRatio, "profileDefaults.woundedHealthRatio",
                "criticalHealthRatio");
        if (!(woundedHealthRatio < 1.0)) {
            throw new ConfigurationException("profileDefaults.woundedHealthRatio", "must be below 1, was "
                    + woundedHealthRatio);
        }
        SettingsChecks.requireGreater(meleeReachBlocks, 0.0, "profileDefaults.meleeReachBlocks", "0");
        SettingsChecks.requireGreater(bandInnerBlocks, meleeReachBlocks, "profileDefaults.bandInnerBlocks",
                "meleeReachBlocks");
        SettingsChecks.requireGreater(bandOuterBlocks, bandInnerBlocks, "profileDefaults.bandOuterBlocks",
                "bandInnerBlocks");
        SettingsChecks.requireAtLeast(rangedReachBlocks, bandOuterBlocks, "profileDefaults.rangedReachBlocks",
                "bandOuterBlocks");
        SettingsChecks.requireGreater(retreatDistanceBlocks, 0.0, "profileDefaults.retreatDistanceBlocks", "0");
        SettingsChecks.requireRange(retreatSpeed, MIN_SPEED, MAX_SPEED, "profileDefaults.retreatSpeed");
        SettingsChecks.requireRange(approachSpeed, MIN_SPEED, MAX_SPEED, "profileDefaults.approachSpeed");
    }

    /**
     * Returns the internal defaults used when {@code config.yml} does not set a value.
     *
     * @return the default values
     */
    public static ProfileDefaults standard() {
        var movement = MovementSettings.DEFAULTS;
        return new ProfileDefaults(2, 20, 40, 0.3, 0.6, movement.meleeReachBlocks(), movement.bandInnerBlocks(),
                movement.bandOuterBlocks(), movement.rangedReachBlocks(), movement.retreatDistanceBlocks(),
                movement.retreatSpeed(), movement.approachSpeed());
    }

    /**
     * Reads the {@code profileDefaults} section; absent keys keep {@link #standard()}.
     *
     * @param configuration root of {@code config.yml}
     * @return the validated defaults
     * @throws ConfigurationException if a value has the wrong type or range
     */
    public static ProfileDefaults from(ConfigurationSection configuration) {
        var standard = standard();
        return new ProfileDefaults(
                integer(configuration, "observationIntervalTicks", standard.observationIntervalTicks()),
                integer(configuration, "minimumDecisionIntervalTicks", standard.minimumDecisionIntervalTicks()),
                integer(configuration, "maximumIntentDurationTicks", standard.maximumIntentDurationTicks()),
                decimal(configuration, "criticalHealthRatio", standard.criticalHealthRatio()),
                decimal(configuration, "woundedHealthRatio", standard.woundedHealthRatio()),
                decimal(configuration, "meleeReachBlocks", standard.meleeReachBlocks()),
                decimal(configuration, "bandInnerBlocks", standard.bandInnerBlocks()),
                decimal(configuration, "bandOuterBlocks", standard.bandOuterBlocks()),
                decimal(configuration, "rangedReachBlocks", standard.rangedReachBlocks()),
                decimal(configuration, "retreatDistanceBlocks", standard.retreatDistanceBlocks()),
                decimal(configuration, "retreatSpeed", standard.retreatSpeed()),
                decimal(configuration, "approachSpeed", standard.approachSpeed()));
    }

    /**
     * Returns the default movement settings, without an arena radius.
     *
     * @return movement settings of a profile without a {@code Movement} section
     */
    public MovementSettings movement() {
        return new MovementSettings(meleeReachBlocks, bandInnerBlocks, bandOuterBlocks, rangedReachBlocks,
                retreatDistanceBlocks, retreatSpeed, approachSpeed, OptionalDouble.empty());
    }

    /**
     * Returns the default health thresholds.
     *
     * @return thresholds of a profile without a {@code Health} section
     */
    public HealthThresholds health() {
        return new HealthThresholds(criticalHealthRatio, woundedHealthRatio);
    }

    private static int integer(ConfigurationSection configuration, String key, int fallback) {
        return ConfigurationValues.intValue(configuration, "profileDefaults." + key, fallback);
    }

    private static double decimal(ConfigurationSection configuration, String key, double fallback) {
        return ConfigurationValues.decimal(configuration, "profileDefaults." + key, fallback);
    }
}
