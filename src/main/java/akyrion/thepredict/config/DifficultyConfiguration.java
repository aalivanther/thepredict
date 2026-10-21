package akyrion.thepredict.config;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.DifficultyMode;
import akyrion.thepredict.domain.DifficultyPreset;
import akyrion.thepredict.domain.PresetNumbers;
import akyrion.thepredict.domain.WorldDifficulty;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code difficulty} section of {@code config.yml} (M22.11). Unknown keys are rejected by the loader. A world
 * name is not listed in advance: {@code difficulty.worlds.<world>.preset} and {@code .entropy} are the only leaves.
 */
public final class DifficultyConfiguration {
    private static final Set<String> PATHS = paths();

    private DifficultyConfiguration() {
    }

    /**
     * Paths of the section, including the section nodes.
     *
     * @return the paths
     */
    public static Set<String> paths() {
        var paths = new LinkedHashSet<String>();
        paths.add("difficulty");
        paths.add("difficulty.defaultMode");
        paths.add("difficulty.defaultPreset");
        paths.add("difficulty.adjustIntervalTicks");
        paths.add("difficulty.toleranceRatio");
        paths.add("difficulty.deadbandRatio");
        paths.add("difficulty.learningGain");
        paths.add("difficulty.projectedTtkCapRatio");
        paths.add("difficulty.dampingReversals");
        paths.add("difficulty.estimatorHalfLifeTicks");
        paths.add("difficulty.minEvidenceTicks");
        paths.add("difficulty.disengageTicks");
        paths.add("difficulty.maxTrackedFights");
        paths.add("difficulty.nativeLevelAllowed");
        paths.add("difficulty.presets");
        paths.add("difficulty.worlds");
        for (var preset : DifficultyPreset.values()) {
            var prefix = "difficulty.presets." + preset.yamlKey();
            paths.add(prefix);
            paths.add(prefix + ".targetMinutes");
            paths.add(prefix + ".reactionFloorTicks");
            paths.add(prefix + ".entropyReactionFloorTicks");
            paths.add(prefix + ".entropyAdaptStartSeconds");
        }
        return Set.copyOf(paths);
    }

    /**
     * Section nodes, which are not leaves.
     *
     * @return the section paths
     */
    public static Set<String> sections() {
        var sections = new LinkedHashSet<String>();
        sections.add("difficulty");
        sections.add("difficulty.presets");
        sections.add("difficulty.worlds");
        for (var preset : DifficultyPreset.values()) {
            sections.add("difficulty.presets." + preset.yamlKey());
        }
        return Set.copyOf(sections);
    }

    /**
     * Whether a path is a known difficulty path or a world imposition.
     *
     * @param path full path
     * @return whether the loader accepts it
     */
    public static boolean allowed(String path) {
        return PATHS.contains(path) || worldPath(path);
    }

    /**
     * A world imposition has exactly two segments after {@code difficulty.worlds}: the world name and
     * {@code preset} or {@code entropy}. The world section itself is also accepted.
     *
     * @param path full path
     * @return whether it is a world path
     */
    public static boolean worldPath(String path) {
        var prefix = "difficulty.worlds.";
        if (!path.startsWith(prefix)) {
            return false;
        }
        var rest = path.substring(prefix.length());
        var dot = rest.indexOf('.');
        if (dot < 0) {
            return !rest.isEmpty();
        }
        if (rest.indexOf('.', dot + 1) >= 0) {
            return false;
        }
        var leaf = rest.substring(dot + 1);
        return leaf.equals("preset") || leaf.equals("entropy");
    }

    /**
     * Reads the section. An absent section is {@link DifficultyGlobals#DEFAULT}, the single default.
     * Absent preset fields use the built-in numbers.
     *
     * @param configuration the file
     * @return the globals
     */
    public static DifficultyGlobals parse(ConfigurationSection configuration) {
        var found = ConfigurationValues.section(configuration, "difficulty");
        if (found.isEmpty()) {
            return DifficultyGlobals.DEFAULT;
        }
        var section = found.get();
        var defaults = DifficultyGlobals.DEFAULT;
        var mode = mode(section, "defaultMode", defaults.defaultMode());
        var preset = preset(section, "defaultPreset", defaults.defaultPreset());
        var numbers = new EnumMap<DifficultyPreset, PresetNumbers>(DifficultyPreset.class);
        for (var value : DifficultyPreset.values()) {
            numbers.put(value, presetNumbers(section, value));
        }
        try {
            return new DifficultyGlobals(mode, preset, numbers, worlds(section),
                    ConfigurationValues.intValue(section, "adjustIntervalTicks", defaults.adjustIntervalTicks()),
                    ConfigurationValues.decimal(section, "toleranceRatio", defaults.toleranceRatio()),
                    ConfigurationValues.decimal(section, "deadbandRatio", defaults.deadbandRatio()),
                    ConfigurationValues.decimal(section, "learningGain", defaults.learningGain()),
                    ConfigurationValues.decimal(section, "projectedTtkCapRatio", defaults.projectedTtkCapRatio()),
                    ConfigurationValues.intValue(section, "dampingReversals", defaults.dampingReversals()),
                    ConfigurationValues.intValue(section, "estimatorHalfLifeTicks", defaults.estimatorHalfLifeTicks()),
                    ConfigurationValues.intValue(section, "minEvidenceTicks", defaults.minEvidenceTicks()),
                    ConfigurationValues.intValue(section, "disengageTicks", defaults.disengageTicks()),
                    ConfigurationValues.intValue(section, "maxTrackedFights", defaults.maxTrackedFights()),
                    ConfigurationValues.booleanValue(section, "nativeLevelAllowed", defaults.nativeLevelAllowed()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("difficulty", exception);
        }
    }

    private static PresetNumbers presetNumbers(ConfigurationSection difficulty, DifficultyPreset preset) {
        var builtIn = PresetNumbers.builtIn(preset);
        var path = "presets." + preset.yamlKey();
        var section = difficulty.getConfigurationSection(path);
        if (section == null) {
            return builtIn;
        }
        try {
            return new PresetNumbers(ConfigurationValues.intValue(section, "targetMinutes", builtIn.targetMinutes()),
                    ConfigurationValues.intValue(section, "reactionFloorTicks", builtIn.reactionFloorTicks()),
                    ConfigurationValues.intValue(section, "entropyReactionFloorTicks",
                            builtIn.entropyReactionFloorTicks()),
                    ConfigurationValues.decimal(section, "entropyAdaptStartSeconds",
                            builtIn.entropyAdaptStartSeconds()));
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf("difficulty." + path, exception);
        }
    }

    private static java.util.Map<String, WorldDifficulty> worlds(ConfigurationSection difficulty) {
        var section = difficulty.getConfigurationSection("worlds");
        if (section == null) {
            return java.util.Map.of();
        }
        var worlds = new HashMap<String, WorldDifficulty>();
        for (var name : section.getKeys(false)) {
            var world = ConfigurationValues.requiredSection(section, name);
            ProfileSchema.requireKnown(world, Set.of("preset", "entropy"));
            Optional<DifficultyPreset> preset = world.contains("preset")
                    ? Optional.of(namedPreset(world, "preset")) : Optional.empty();
            Optional<Boolean> entropy = world.contains("entropy")
                    ? Optional.of(ConfigurationValues.booleanValue(world, "entropy", false)) : Optional.empty();
            worlds.put(name, new WorldDifficulty(preset, entropy));
        }
        return worlds;
    }

    private static DifficultyMode mode(ConfigurationSection section, String key, DifficultyMode fallback) {
        if (!section.contains(key)) {
            return fallback;
        }
        var text = ConfigurationValues.requiredText(section, key);
        try {
            return DifficultyMode.parse(text);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, key),
                    "must be auto, bench or player");
        }
    }

    private static DifficultyPreset preset(ConfigurationSection section, String key, DifficultyPreset fallback) {
        if (!section.contains(key)) {
            return fallback;
        }
        return namedPreset(section, key);
    }

    private static DifficultyPreset namedPreset(ConfigurationSection section, String key) {
        try {
            return DifficultyPreset.parse(ConfigurationValues.requiredText(section, key));
        } catch (RuntimeException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, key),
                    "must be one of facil, normal, dificil, caos, vacio");
        }
    }
}
