package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;

/**
 * Live ceiling of a distilled mode (M29.7). {@code distill mode} is the only rise. {@code distill disable}
 * is visible on the next capture. A missing verdict stays off.
 */
public final class DistillControl {
    private final DistillMode ceiling;
    private final DistillMode verdict;
    private volatile boolean disabled;
    private volatile DistillMode commanded;

    /**
     * A control that cannot rise above off. Used until the plugin installs the real ceiling.
     *
     * @return the control
     */
    public static DistillControl passthrough() {
        return new DistillControl(DistillMode.OFF, DistillMode.OFF);
    }

    /**
     * Creates the control.
     *
     * @param ceiling the global {@code distill.mode}
     * @param verdict the highest mode a recorded verdict allows
     */
    public DistillControl(DistillMode ceiling, DistillMode verdict) {
        this.ceiling = ceiling;
        this.verdict = verdict;
        this.commanded = ceiling.lower(verdict);
    }

    /**
     * The highest mode a loaded verdict allows. Off means no verdict was loaded.
     *
     * @return the verdict ceiling
     */
    public DistillMode verdict() {
        return verdict;
    }

    /**
     * Raises up to the minimum of the request, the global and the verdict, and clears disable.
     *
     * @param asked the requested mode
     * @return the mode that will apply
     */
    public DistillMode raise(DistillMode asked) {
        var allowed = asked.lower(ceiling).lower(verdict);
        commanded = allowed;
        disabled = false;
        return allowed;
    }

    /**
     * Drops the next capture to off. It does not touch a learned artifact.
     */
    public void disable() {
        disabled = true;
    }

    /**
     * The mode of the next capture.
     *
     * @param configured the mode of the loaded settings
     * @return off after disable, otherwise the minimum
     */
    public DistillMode effective(DistillMode configured) {
        if (disabled) {
            return DistillMode.OFF;
        }
        return configured.lower(commanded).lower(verdict);
    }
}
