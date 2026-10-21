package akyrion.thepredict.domain;

/**
 * Hysteresis of one zone fact (M24.7). A fact enters after {@code onTicks} consecutive raw hits and leaves after
 * {@code offTicks} consecutive raw misses. Two changes of the same fact are at least {@code offTicks} apart. A raw
 * value that alternates faster than {@code onTicks} never enters, because each miss resets the run of hits.
 */
public final class ZoneFactPolicy {
    private int trueRun;
    private int falseRun;
    private boolean active;
    private int lastChange = Integer.MIN_VALUE / 4;

    /**
     * Creates a filter that starts inactive.
     */
    public ZoneFactPolicy() {
    }

    /**
     * Advances one tick of raw evidence.
     *
     * @param raw whether the fact is true before hysteresis
     * @param tick current tick
     * @param onTicks consecutive raw ticks required to enter
     * @param offTicks consecutive raw ticks required to leave, also the gap between changes
     * @return whether the filtered fact changed on this tick
     */
    public boolean advance(boolean raw, int tick, int onTicks, int offTicks) {
        if (raw) {
            trueRun++;
            falseRun = 0;
        } else {
            falseRun++;
            trueRun = 0;
        }
        if (!active && trueRun >= onTicks && tick - lastChange >= offTicks) {
            active = true;
            lastChange = tick;
            trueRun = 0;
            return true;
        }
        if (active && falseRun >= offTicks && tick - lastChange >= offTicks) {
            active = false;
            lastChange = tick;
            falseRun = 0;
            return true;
        }
        return false;
    }

    /**
     * Whether the filtered fact is on.
     *
     * @return the filtered value
     */
    public boolean active() {
        return active;
    }
}
