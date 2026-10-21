package akyrion.thepredict.domain;

/**
 * Readiness of one choice (M20.7). Only {@link ReadyKind#EXHAUSTED} fails an old answer. A vanilla melee choice is
 * never asked: the engine treats it as zero ticks.
 *
 * @param kind what the port could read
 * @param ticks ticks until ready; {@code 0} unless the kind is {@link ReadyKind#READY_IN}
 */
public record ReadyIn(ReadyKind kind, int ticks) {
    /** The choice will never recharge. */
    public static final ReadyIn EXHAUSTED = new ReadyIn(ReadyKind.EXHAUSTED, 0);
    /** The port cannot say. */
    public static final ReadyIn UNKNOWN = new ReadyIn(ReadyKind.UNKNOWN, 0);

    /**
     * Validates the pair.
     *
     * @throws IllegalArgumentException if the ticks do not match the kind
     */
    public ReadyIn {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (ticks < 0 || kind != ReadyKind.READY_IN && ticks != 0) {
            throw new IllegalArgumentException("ticks must be 0 unless the kind is READY_IN, was " + ticks);
        }
    }

    /**
     * A finite wait.
     *
     * @param ticks ticks until ready, {@code >= 0}
     * @return the reading
     */
    public static ReadyIn in(int ticks) {
        return new ReadyIn(ReadyKind.READY_IN, ticks);
    }
}
