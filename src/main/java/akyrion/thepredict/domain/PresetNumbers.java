package akyrion.thepredict.domain;

/**
 * One preset's numbers after config (M22.13). The built-in values live on {@link DifficultyPreset}.
 *
 * @param targetMinutes target length of the whole fight, in minutes
 * @param reactionFloorTicks floor against players, 0 to 20
 * @param entropyReactionFloorTicks entropy floor, 0 to 5
 * @param entropyAdaptStartSeconds adaptation at t = 0, 0 to 30
 */
public record PresetNumbers(int targetMinutes, int reactionFloorTicks, int entropyReactionFloorTicks,
                            double entropyAdaptStartSeconds) {
    /**
     * Checks the ranges.
     *
     * @throws IllegalArgumentException if a number is outside its range
     */
    public PresetNumbers {
        if (targetMinutes < 1 || targetMinutes > 60) {
            throw new IllegalArgumentException("targetMinutes must be within [1, 60], was " + targetMinutes);
        }
        if (reactionFloorTicks < 0 || reactionFloorTicks > 20) {
            throw new IllegalArgumentException("reactionFloorTicks must be within [0, 20], was " + reactionFloorTicks);
        }
        if (entropyReactionFloorTicks < 0 || entropyReactionFloorTicks > 5) {
            throw new IllegalArgumentException("entropyReactionFloorTicks must be within [0, 5], was "
                    + entropyReactionFloorTicks);
        }
        if (!(entropyAdaptStartSeconds >= 0.0) || entropyAdaptStartSeconds > 30.0) {
            throw new IllegalArgumentException("entropyAdaptStartSeconds must be within [0, 30], was "
                    + entropyAdaptStartSeconds);
        }
    }

    /**
     * The built-in numbers of a preset.
     *
     * @param preset the preset
     * @return those numbers
     */
    public static PresetNumbers builtIn(DifficultyPreset preset) {
        return new PresetNumbers(preset.targetMinutes(), preset.reactionFloorTicks(),
                preset.entropyReactionFloorTicks(), preset.entropyAdaptStartSeconds());
    }

    /**
     * Target length in seconds.
     *
     * @return {@code targetMinutes × 60}
     */
    public int targetSeconds() {
        return targetMinutes * 60;
    }
}
