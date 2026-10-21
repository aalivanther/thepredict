package akyrion.thepredict.engine.learn;

import java.util.Arrays;

/**
 * Damage taken over the last 60 ticks (M28.2), in fractions of maximum health. One slot per server tick: a slot
 * whose stamp left the 60-tick span counts as no damage, so an old hit never looks recent. Two arrays, no
 * allocation on update.
 *
 * <p>{@link #add(int)} and {@link #rate()} are the legacy entry points of a caller that does not pass the tick.
 * They advance one slot per damage event on a private event clock, so the rate is per event, not per tick. A caller
 * uses either the tick pair or the legacy pair, never both.
 */
public final class DamageRateRing {
    /** Ticks remembered. */
    public static final int TICKS = 60;
    private static final long EMPTY = Long.MIN_VALUE;

    private final double[] taken = new double[TICKS];
    private final long[] stamps = new long[TICKS];
    private long events;

    /**
     * Creates an empty ring.
     */
    public DamageRateRing() {
        Arrays.fill(stamps, EMPTY);
    }

    /**
     * Records damage taken on one tick. Several hits on the same tick add up. A non-positive or non-finite fraction
     * is ignored, and one hit counts at most the whole health.
     *
     * @param tick server tick of the hit
     * @param fraction damage as a fraction of maximum health
     */
    public void add(long tick, double fraction) {
        if (!(fraction > 0.0) || !Double.isFinite(fraction)) {
            return;
        }
        var slot = (int) Math.floorMod(tick, (long) TICKS);
        if (stamps[slot] != tick) {
            stamps[slot] = tick;
            taken[slot] = 0.0;
        }
        taken[slot] += Math.min(1.0, fraction);
    }

    /**
     * Mean damage per tick over the 60 ticks that end at {@code tick}, both included.
     *
     * @param tick server tick the span ends at
     * @return fraction of maximum health per tick
     */
    public double rate(long tick) {
        var sum = 0.0;
        for (var slot = 0; slot < TICKS; slot++) {
            var stamp = stamps[slot];
            if (stamp != EMPTY && stamp <= tick && tick - stamp < TICKS) {
                sum += taken[slot];
            }
        }
        return sum / TICKS;
    }

    /**
     * Legacy entry point: records one damage event on the next event slot.
     *
     * @param amount damage of this event in thousandths of maximum health
     */
    public void add(int amount) {
        add(events++, amount / 1000.0);
    }

    /**
     * Legacy entry point: mean damage per event slot over the last 60 events, in fractions of maximum health.
     *
     * @return the rate
     */
    public double rate() {
        return rate(events - 1);
    }
}
