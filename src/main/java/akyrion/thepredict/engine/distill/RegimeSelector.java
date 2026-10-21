package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.RegimeKey;

/**
 * Regime dwell of one session (M29.5). The candidate must hold for {@code dwellTicks} counted from the event.
 * The switch is applied only on a capture. A telegraph blocks the switch. The cap is per encounter.
 */
public final class RegimeSelector {
    private final int dwellTicks;
    private final int maxSwitches;
    private RegimeKey current = RegimeKey.ALL;
    private RegimeKey pending = RegimeKey.ALL;
    private long pendingSince = Long.MIN_VALUE;
    private int switches;
    private boolean blocked;

    /**
     * Creates a selector.
     *
     * @param dwellTicks ticks the candidate must hold
     * @param maxSwitches switches allowed in one encounter
     */
    public RegimeSelector(int dwellTicks, int maxSwitches) {
        this.dwellTicks = dwellTicks;
        this.maxSwitches = maxSwitches;
    }

    /**
     * The regime in force.
     *
     * @return the key
     */
    public RegimeKey current() {
        return current;
    }

    /**
     * Switches already applied.
     *
     * @return the count
     */
    public int switches() {
        return switches;
    }

    /**
     * Notes a telegraph. While blocked, a capture does not switch.
     *
     * @param open whether a telegraph is open
     */
    public void telegraph(boolean open) {
        blocked = open;
    }

    /**
     * Offers a candidate. The clock starts when the candidate changes.
     *
     * @param candidate the observed key
     * @param tick the event tick
     */
    public void offer(RegimeKey candidate, long tick) {
        if (!candidate.equals(pending)) {
            pending = candidate;
            pendingSince = tick;
        }
    }

    /**
     * Applies a due switch on a capture.
     *
     * @param tick the capture tick
     * @return whether the regime changed
     */
    public boolean onCapture(long tick) {
        if (blocked || pending.equals(current)) {
            return false;
        }
        if (tick - pendingSince < dwellTicks) {
            return false;
        }
        if (switches >= maxSwitches) {
            return false;
        }
        current = pending;
        switches++;
        return true;
    }

    /**
     * Whether the next capture would be refused because the cap is full.
     *
     * @param tick the server tick
     * @return {@code true} when a different pending key is due and the cap is full
     */
    public boolean capped(long tick) {
        return switches >= maxSwitches && !pending.equals(current) && tick - pendingSince >= dwellTicks && !blocked;
    }
}
