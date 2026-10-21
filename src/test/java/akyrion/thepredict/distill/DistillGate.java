package akyrion.thepredict.distill;

import akyrion.thepredict.domain.distill.DistillMode;

/**
 * Mode gate (M29.6). Autonomous without a champion tape is inconclusive. The tape is not recorded here.
 */
public final class DistillGate {
    private DistillGate() {
    }

    /**
     * The verdict.
     *
     * @param mode the requested mode
     * @param tape whether a fresh champion tape exists
     * @param scope {@code bench} or {@code all}
     * @return the verdict token
     */
    public static String verdict(DistillMode mode, boolean tape, String scope) {
        if (mode == DistillMode.AUTONOMOUS && !tape) {
            return "INCONCLUSIVE";
        }
        if (mode == DistillMode.SHADOW) {
            return "PASS";
        }
        if (mode == DistillMode.ASSIST && "bench".equals(scope)) {
            return "PASS_SIM_ONLY";
        }
        if (mode == DistillMode.ASSIST || mode == DistillMode.AUTONOMOUS) {
            return "PASS";
        }
        return "INCONCLUSIVE";
    }
}
