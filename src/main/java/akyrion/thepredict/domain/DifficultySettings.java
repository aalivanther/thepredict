package akyrion.thepredict.domain;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The {@code Difficulty} section of a profile (M22.11). Absent fields inherit the global config. Target seconds are
 * optional (errata E22-3): an absent value is not an error and the preset supplies T.
 *
 * @param mode empty inherits {@code difficulty.defaultMode}
 * @param targetSeconds empty uses the resolved preset; present is 5 to 3600
 * @param tolerance empty inherits {@code difficulty.toleranceRatio}
 * @param minRung lowest rung, 0 to 8
 * @param maxRung highest rung, 0 to 8, at least {@code minRung}
 * @param startRung empty means auto
 * @param inFight whether the fast loop may move the rung
 * @param learn whether the slow loop may move the prior
 * @param nativeLevel whether the next spawn may move the MythicMobs level
 */
public record DifficultySettings(Optional<DifficultyMode> mode, OptionalInt targetSeconds, OptionalDouble tolerance,
                                 int minRung, int maxRung, OptionalInt startRung, boolean inFight, boolean learn,
                                 NativeLevelMode nativeLevel) {
    /** Section omitted: inherit mode, tolerance and T; rungs 0–8; both loops on; native level off. */
    public static final DifficultySettings DEFAULT = new DifficultySettings(Optional.empty(), OptionalInt.empty(),
            OptionalDouble.empty(), 0, 8, OptionalInt.empty(), true, true, NativeLevelMode.OFF);

    /**
     * Checks the ranges. An empty target is allowed.
     *
     * @throws IllegalArgumentException if a present value is outside its range or {@code minRung > maxRung}
     */
    public DifficultySettings {
        if (targetSeconds.isPresent() && (targetSeconds.getAsInt() < 5 || targetSeconds.getAsInt() > 3600)) {
            throw new IllegalArgumentException("targetSeconds must be within [5, 3600], was "
                    + targetSeconds.getAsInt());
        }
        if (tolerance.isPresent() && (!(tolerance.getAsDouble() >= 0.05) || tolerance.getAsDouble() > 0.50)) {
            throw new IllegalArgumentException("tolerance must be within [0.05, 0.50], was " + tolerance.getAsDouble());
        }
        if (minRung < 0 || minRung > 8 || maxRung < 0 || maxRung > 8 || minRung > maxRung) {
            throw new IllegalArgumentException("rungs must satisfy 0 <= min <= max <= 8, was " + minRung + ".."
                    + maxRung);
        }
        if (startRung.isPresent() && (startRung.getAsInt() < 0 || startRung.getAsInt() > 8)) {
            throw new IllegalArgumentException("startRung must be within [0, 8], was " + startRung.getAsInt());
        }
    }
}
