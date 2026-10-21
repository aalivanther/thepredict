package akyrion.thepredict.admin;

import akyrion.thepredict.domain.DifficultyGlobals;

/**
 * Two lines an administrator sees for difficulty (M22.11). A player sees nothing: there is no player form.
 */
public final class DifficultyFormatter {
    private DifficultyFormatter() {
    }

    /**
     * The administrator block: preset, entropy, {@code r(t)}, {@code c(t)} and the rung.
     *
     * @param preset effective preset name
     * @param entropy whether entropy is on
     * @param reactionTicks {@code r(t)}
     * @param adaptSeconds {@code c(t)}
     * @param rung current rung, or {@code -1} when bench
     * @return two lines separated by a newline
     */
    public static String admin(String preset, boolean entropy, int reactionTicks, double adaptSeconds, int rung) {
        var pressure = rung < 0 ? "bench" : Integer.toString(rung);
        return "preset " + preset + " entropy " + entropy + "\nr " + reactionTicks + " c " + adaptSeconds
                + " pressure " + pressure;
    }

    /**
     * The block of {@code eval status} without a nearby session: the configured default preset at bench, with its
     * configured reaction floor (CAR-21).
     *
     * @param globals the {@code difficulty} section in use
     * @return two lines separated by a newline
     */
    public static String bench(DifficultyGlobals globals) {
        var preset = globals.defaultPreset();
        return admin(preset.displayName(), false, globals.numbers(preset).reactionFloorTicks(), 0.0, -1);
    }
}
