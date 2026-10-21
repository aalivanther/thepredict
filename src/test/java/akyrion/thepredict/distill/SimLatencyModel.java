package akyrion.thepredict.distill;

/**
 * Published tick bands for a distilled round (M29.0). Jev stays on the measured 5 to 6 ticks. The student is one
 * tick. This does not change {@code LatencyModel}.
 */
public final class SimLatencyModel {
    private SimLatencyModel() {
    }

    /**
     * Ticks of a Jev sample inside 250 to 300 ms.
     *
     * @param millis milliseconds
     * @return 5 or 6
     */
    public static int jevTicks(int millis) {
        if (millis < 250 || millis > 300) {
            throw new IllegalArgumentException("jev band");
        }
        return (int) Math.ceil(millis / 50.0);
    }

    /**
     * Ticks of the student. The decision is applied on the next tick.
     *
     * @return 1
     */
    public static int distilledTicks() {
        return 1;
    }
}
