package akyrion.thepredict.eval;

/**
 * The verdict of a gate of stages (M22.5): the worst stage wins. A failure beats everything, an inconclusive stage
 * never passes, a skipped stage changes nothing, and one stage that only the simulator passed makes the whole verdict
 * {@code PASS_SIM_ONLY}. The effectiveness bench of the duel simulator reads its validity gates and its cells through
 * it.
 */
public final class EvalGate {
    /** A stage or a verdict that passed. */
    public static final String PASS = "PASS";
    /** Passed in the simulator only. */
    public static final String PASS_SIM_ONLY = "PASS_SIM_ONLY";
    /** Failed. */
    public static final String FAIL = "FAIL";
    /** Could not be decided. */
    public static final String INCONCLUSIVE = "INCONCLUSIVE";
    /** Not run. */
    public static final String SKIPPED = "SKIPPED";

    private EvalGate() {
    }

    /**
     * The verdict of the stages.
     *
     * @param stages one status per stage
     * @return {@code FAIL}, {@code INCONCLUSIVE}, {@code PASS_SIM_ONLY} or {@code PASS}
     * @throws IllegalArgumentException for an unknown status
     */
    public static String overall(String... stages) {
        var sawSim = false;
        var sawFail = false;
        var sawInconclusive = false;
        for (var stage : stages) {
            switch (stage) {
                case FAIL -> sawFail = true;
                case INCONCLUSIVE -> sawInconclusive = true;
                case PASS_SIM_ONLY -> sawSim = true;
                case PASS, SKIPPED -> {
                }
                default -> throw new IllegalArgumentException("unknown stage status " + stage);
            }
        }
        if (sawFail) {
            return FAIL;
        }
        if (sawInconclusive) {
            return INCONCLUSIVE;
        }
        return sawSim ? PASS_SIM_ONLY : PASS;
    }
}
