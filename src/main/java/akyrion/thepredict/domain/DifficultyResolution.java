package akyrion.thepredict.domain;

/**
 * Precedence of preset, entropy and T (M22.13). A world preset imposed by an administrator beats an explicit target
 * time. The preset still supplies the floors when T comes from seconds.
 */
public final class DifficultyResolution {
    /** Sentinel for an absent target time. */
    public static final int ABSENT = -1;

    private DifficultyResolution() {
    }

    /**
     * The preset that wins.
     *
     * @param world administrator preset, or {@code null}
     * @param variable Mythic variable, or {@code null}
     * @param mobFile mob file, or {@code null}
     * @param fallback configured default
     * @return the preset
     */
    public static DifficultyPreset preset(DifficultyPreset world, DifficultyPreset variable, DifficultyPreset mobFile,
                                          DifficultyPreset fallback) {
        if (world != null) {
            return world;
        }
        if (variable != null) {
            return variable;
        }
        if (mobFile != null) {
            return mobFile;
        }
        return fallback;
    }

    /**
     * Target length in seconds.
     *
     * @param worldImposed whether an administrator imposed the world preset
     * @param worldSeconds T of that preset
     * @param variableSeconds Mythic variable, or {@link #ABSENT}
     * @param profileSeconds profile {@code TargetSeconds}, or {@link #ABSENT}
     * @param presetSeconds T of the effective preset
     * @return the seconds
     */
    public static int targetSeconds(boolean worldImposed, int worldSeconds, int variableSeconds, int profileSeconds,
                                    int presetSeconds) {
        if (worldImposed) {
            return worldSeconds;
        }
        if (variableSeconds != ABSENT) {
            return variableSeconds;
        }
        if (profileSeconds != ABSENT) {
            return profileSeconds;
        }
        return presetSeconds;
    }

    /**
     * Whether entropy is on. A world value wins over the mob file.
     *
     * @param world administrator value, or {@code null} when the world is silent
     * @param mobFile mob file value
     * @return whether entropy is on
     */
    public static boolean entropy(Boolean world, boolean mobFile) {
        return world != null ? world : mobFile;
    }

    /**
     * Resolves {@link DifficultyMode#AUTO}.
     *
     * @param mode the configured mode
     * @param player whether the target is a player
     * @return the play mode
     */
    public static PlayMode play(DifficultyMode mode, boolean player) {
        return switch (mode) {
            case BENCH -> PlayMode.BENCH;
            case PLAYER -> PlayMode.PLAYER;
            case AUTO -> player ? PlayMode.PLAYER : PlayMode.BENCH;
        };
    }
}
