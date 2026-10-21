package akyrion.thepredict.engine;

import akyrion.thepredict.domain.PatternMode;

/**
 * Effective pattern mode (M26.6). Without a verdict, {@code bias} and {@code full} stay at {@code observe} and the
 * status names {@code UNVERIFIED_PATTERN_MODE}. {@code off} does not need a verdict.
 */
public final class PatternModeGate {
    /** Cause published when a mode above observe has no verdict. */
    public static final String UNVERIFIED = "UNVERIFIED_PATTERN_MODE";

    private PatternMode effective = PatternMode.OBSERVE;
    private String cause = UNVERIFIED;

    /**
     * Starts at observe with the unverified cause.
     */
    public PatternModeGate() {
    }

    /**
     * Accepts the configured mode.
     *
     * @param requested the mode the file asked for
     * @param passed whether a verdict of {@code PASS} is loaded
     */
    public void offer(PatternMode requested, boolean passed) {
        if (requested == PatternMode.OFF) {
            effective = PatternMode.OFF;
            cause = "";
            return;
        }
        if (!passed && requested != PatternMode.OBSERVE) {
            effective = PatternMode.OBSERVE;
            cause = UNVERIFIED;
            return;
        }
        effective = requested;
        cause = passed ? "" : UNVERIFIED;
    }

    /**
     * The mode the engine may use.
     *
     * @return the effective mode
     */
    public PatternMode effective() {
        return effective;
    }

    /**
     * Why the requested mode was not used. Empty when the verdict passed or the mode is {@code off}.
     *
     * @return the cause, or empty
     */
    public String cause() {
        return cause;
    }
}
