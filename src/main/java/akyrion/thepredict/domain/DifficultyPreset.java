package akyrion.thepredict.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Named target time and floors (M22.13). YAML keys are {@code facil|normal|dificil|caos|vacio}. Mob files accept the
 * accented names. One default, here.
 */
public enum DifficultyPreset {
    /** Forty-five minutes, floor 5, entropy floor 4, adaptation 7 s. */
    FACIL("Fácil", "facil", 45, 5, 4, 7.0),
    /** Thirty minutes, floor 5, entropy floor 3, adaptation 5 s. */
    NORMAL("Normal", "normal", 30, 5, 3, 5.0),
    /** Fifteen minutes, floor 3, entropy floor 0, adaptation 3.5 s. */
    DIFICIL("Difícil", "dificil", 15, 3, 0, 3.5),
    /** Ten minutes, floor 1, entropy floor 0, adaptation 1.5 s. */
    CAOS("Caos", "caos", 10, 1, 0, 1.5),
    /** Five minutes, floor 0, entropy floor 0, adaptation 0 s. */
    VACIO("Vacío", "vacio", 5, 0, 0, 0.0);

    /** Delivery default. */
    public static final DifficultyPreset DEFAULT = NORMAL;

    private final String displayName;
    private final String yamlKey;
    private final int targetMinutes;
    private final int reactionFloorTicks;
    private final int entropyReactionFloorTicks;
    private final double entropyAdaptStartSeconds;

    DifficultyPreset(String displayName, String yamlKey, int targetMinutes, int reactionFloorTicks,
                     int entropyReactionFloorTicks, double entropyAdaptStartSeconds) {
        this.displayName = displayName;
        this.yamlKey = yamlKey;
        this.targetMinutes = targetMinutes;
        this.reactionFloorTicks = reactionFloorTicks;
        this.entropyReactionFloorTicks = entropyReactionFloorTicks;
        this.entropyAdaptStartSeconds = entropyAdaptStartSeconds;
    }

    /**
     * Name shown to authors.
     *
     * @return the accented name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Key under {@code difficulty.presets}.
     *
     * @return the yaml key
     */
    public String yamlKey() {
        return yamlKey;
    }

    /**
     * Built-in target, in minutes.
     *
     * @return the minutes
     */
    public int targetMinutes() {
        return targetMinutes;
    }

    /**
     * Built-in target, in seconds.
     *
     * @return {@code targetMinutes × 60}
     */
    public int targetSeconds() {
        return targetMinutes * 60;
    }

    /**
     * Built-in reaction floor against players, in ticks.
     *
     * @return the floor
     */
    public int reactionFloorTicks() {
        return reactionFloorTicks;
    }

    /**
     * Built-in entropy floor, in ticks (errata E22-4).
     *
     * @return the floor
     */
    public int entropyReactionFloorTicks() {
        return entropyReactionFloorTicks;
    }

    /**
     * Built-in entropy adaptation at the start of the fight, in seconds.
     *
     * @return the seconds
     */
    public double entropyAdaptStartSeconds() {
        return entropyAdaptStartSeconds;
    }

    /**
     * Parses a yaml key or a mob-file name. Case and accents are ignored.
     *
     * @param text the token
     * @return the preset
     * @throws IllegalArgumentException if it is not one of the five names
     */
    public static DifficultyPreset parse(String text) {
        var folded = fold(text);
        for (var preset : values()) {
            if (preset.yamlKey.equals(folded) || fold(preset.displayName).equals(folded)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("preset");
    }

    /**
     * Folds case and accents.
     *
     * @param text the token
     * @return the folded token
     */
    public static String fold(String text) {
        var stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT);
    }
}
