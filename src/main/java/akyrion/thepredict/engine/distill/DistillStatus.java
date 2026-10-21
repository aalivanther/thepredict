package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;

/**
 * One status line for {@code /thepredict distill status} (M29.6). It names no player.
 */
public final class DistillStatus {
    private DistillStatus() {
    }

    /**
     * The line.
     *
     * @param state artifact state
     * @param mode effective mode
     * @param regime regime key
     * @return the line
     */
    public static String line(DistilledArtifacts.State state, DistillMode mode, String regime) {
        return "state=" + state.name() + " mode=" + mode.name().toLowerCase(java.util.Locale.ROOT)
                + " regime=" + regime;
    }

    /**
     * The line of the running server. The server holds no distilled-artifact lifecycle, so the state is
     * {@link DistilledArtifacts.State#NONE}; the mode is the one the next capture uses after {@code distill mode}
     * and {@code distill disable}.
     *
     * @param control the live distill control
     * @param configured {@code distill.mode} of the loaded settings
     * @return the line
     */
    public static String line(DistillControl control, DistillMode configured) {
        return line(DistilledArtifacts.State.NONE, control.effective(configured),
                akyrion.thepredict.domain.distill.RegimeKey.ALL.text());
    }
}
