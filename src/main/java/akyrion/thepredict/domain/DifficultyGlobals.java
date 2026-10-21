package akyrion.thepredict.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code difficulty} section of {@code config.yml} (M22.11). One default, {@link #DEFAULT}.
 *
 * @param defaultMode mode when a profile does not set one
 * @param defaultPreset preset when nobody names one
 * @param presets numbers of each preset; missing keys use {@link PresetNumbers#builtIn}
 * @param worlds per-world impositions; the world name is the map key
 * @param adjustIntervalTicks ticks between fast-loop checks, 40 to 600
 * @param toleranceRatio fast-loop band, 0.05 to 0.50
 * @param deadbandRatio slow-loop band, 0 to 0.50
 * @param learningGain slow-loop gain before the clip, 0.05 to 0.50
 * @param projectedTtkCapRatio cap of a projected time to kill, as a multiple of T, 2 to 10
 * @param dampingReversals sign changes in ten fights that halve the gain, 3 to 9
 * @param estimatorHalfLifeTicks base half-life, scaled with T, 20 to 600
 * @param minEvidenceTicks ticks before an estimate counts, 20 to 400
 * @param disengageTicks quiet ticks that end a fight, 40 to 2400
 * @param maxTrackedFights clocks kept per mob, 1 to 16
 * @param nativeLevelAllowed whether any profile may move a spawn level
 */
public record DifficultyGlobals(DifficultyMode defaultMode, DifficultyPreset defaultPreset,
                                Map<DifficultyPreset, PresetNumbers> presets, Map<String, WorldDifficulty> worlds,
                                int adjustIntervalTicks, double toleranceRatio, double deadbandRatio,
                                double learningGain, double projectedTtkCapRatio, int dampingReversals,
                                int estimatorHalfLifeTicks, int minEvidenceTicks, int disengageTicks,
                                int maxTrackedFights, boolean nativeLevelAllowed) {
    /** The delivered section. */
    public static final DifficultyGlobals DEFAULT = standard();

    /** Design gain of the uncalibrated ladder. */
    public static final double LADDER_K = 0.12;
    /** Lower clip of the ladder gain. */
    public static final double LADDER_K_MIN = 0.06;
    /** Upper clip of the ladder gain. */
    public static final double LADDER_K_MAX = 0.18;

    /**
     * Copies the maps and checks the ranges.
     *
     * @throws IllegalArgumentException if a knob is outside its range
     */
    public DifficultyGlobals {
        Objects.requireNonNull(defaultMode, "defaultMode");
        Objects.requireNonNull(defaultPreset, "defaultPreset");
        presets = Map.copyOf(presets);
        worlds = Map.copyOf(worlds);
        checkLoops(adjustIntervalTicks, toleranceRatio, deadbandRatio, learningGain, projectedTtkCapRatio);
        checkEstimator(dampingReversals, estimatorHalfLifeTicks, minEvidenceTicks, disengageTicks, maxTrackedFights);
    }

    private static void checkLoops(int adjustIntervalTicks, double toleranceRatio, double deadbandRatio,
                                   double learningGain, double projectedTtkCapRatio) {
        if (adjustIntervalTicks < 40 || adjustIntervalTicks > 600) {
            throw new IllegalArgumentException("adjustIntervalTicks must be within [40, 600], was "
                    + adjustIntervalTicks);
        }
        if (!(toleranceRatio >= 0.05) || toleranceRatio > 0.50) {
            throw new IllegalArgumentException("toleranceRatio must be within [0.05, 0.50], was " + toleranceRatio);
        }
        if (!(deadbandRatio >= 0.0) || deadbandRatio > 0.50) {
            throw new IllegalArgumentException("deadbandRatio must be within [0, 0.50], was " + deadbandRatio);
        }
        if (!(learningGain >= 0.05) || learningGain > 0.50) {
            throw new IllegalArgumentException("learningGain must be within [0.05, 0.50], was " + learningGain);
        }
        if (!(projectedTtkCapRatio >= 2.0) || projectedTtkCapRatio > 10.0) {
            throw new IllegalArgumentException("projectedTtkCapRatio must be within [2, 10], was "
                    + projectedTtkCapRatio);
        }
    }

    private static void checkEstimator(int dampingReversals, int estimatorHalfLifeTicks, int minEvidenceTicks,
                                       int disengageTicks, int maxTrackedFights) {
        if (dampingReversals < 3 || dampingReversals > 9) {
            throw new IllegalArgumentException("dampingReversals must be within [3, 9], was " + dampingReversals);
        }
        if (estimatorHalfLifeTicks < 20 || estimatorHalfLifeTicks > 600) {
            throw new IllegalArgumentException("estimatorHalfLifeTicks must be within [20, 600], was "
                    + estimatorHalfLifeTicks);
        }
        if (minEvidenceTicks < 20 || minEvidenceTicks > 400) {
            throw new IllegalArgumentException("minEvidenceTicks must be within [20, 400], was " + minEvidenceTicks);
        }
        if (disengageTicks < 40 || disengageTicks > 2400) {
            throw new IllegalArgumentException("disengageTicks must be within [40, 2400], was " + disengageTicks);
        }
        if (maxTrackedFights < 1 || maxTrackedFights > 16) {
            throw new IllegalArgumentException("maxTrackedFights must be within [1, 16], was " + maxTrackedFights);
        }
    }

    /**
     * Built-in presets and the delivered knobs.
     *
     * @return the default section
     */
    public static DifficultyGlobals standard() {
        var presets = new EnumMap<DifficultyPreset, PresetNumbers>(DifficultyPreset.class);
        for (var preset : DifficultyPreset.values()) {
            presets.put(preset, PresetNumbers.builtIn(preset));
        }
        return new DifficultyGlobals(DifficultyMode.DEFAULT, DifficultyPreset.DEFAULT, presets, Map.of(), 100, 0.20,
                0.10, 0.25, 4.0, 4, 100, 60, 200, 4, false);
    }

    /**
     * Numbers of one preset.
     *
     * @param preset the preset
     * @return the configured numbers, or the built-in ones
     */
    public PresetNumbers numbers(DifficultyPreset preset) {
        return presets.getOrDefault(preset, PresetNumbers.builtIn(preset));
    }

    /**
     * Effective learning gain after the ladder clip {@code k_min / k_max}.
     *
     * @return the smaller of {@code learningGain} and that ratio
     */
    public double effectiveGain() {
        return Math.min(learningGain, LADDER_K_MIN / LADDER_K_MAX);
    }
}
