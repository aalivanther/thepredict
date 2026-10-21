package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.StableRandom;

/**
 * Seeded Jev latency in ticks (M20.10, rule P2). A sample is a tick count, never a wait. The lognormal fits the
 * measured 179 / 228 ms; its p99 is about 252 ms, below the 328 ms measured on {@code POST /v1/systemone}, so
 * {@link #measured} adds a tail (§7.5 of the simulator audit): 1 % of answers between 330 and 800 ms and 0.5 % never
 * answered, which {@link #ticks} reports as {@value #LOST}.
 */
public final class LatencyModel {
    /** Natural log of the measured p50, 179 ms. */
    public static final double MU = StrictMath.log(179.0);
    /** Sigma of the measured pair 179 / 228. */
    public static final double SIGMA = StrictMath.log(228.0 / 179.0) / 1.645;
    /** The ticks of an answer that never arrives. */
    public static final int LOST = -1;
    private static final int SALT_TAIL = 13;
    private static final int SALT_TAIL_MILLIS = 14;

    private final long seed;
    private final boolean fixed;
    private final int fixedTicks;
    private final double tailShare;
    private final int tailMinMillis;
    private final int tailMaxMillis;
    private final double lostShare;

    private LatencyModel(long seed, boolean fixed, int fixedTicks, double tailShare, int tailMinMillis,
                         int tailMaxMillis, double lostShare) {
        this.seed = seed;
        this.fixed = fixed;
        this.fixedTicks = fixedTicks;
        this.tailShare = tailShare;
        this.tailMinMillis = tailMinMillis;
        this.tailMaxMillis = tailMaxMillis;
        this.lostShare = lostShare;
    }

    /** Lognormal samples around the measured 179 / 228 ms. */
    public static LatencyModel lognormal(long seed) {
        return new LatencyModel(seed, false, 0, 0, 0, 0, 0);
    }

    /**
     * The lognormal with a tail of slow answers and a share of answers that never arrive.
     *
     * @param seed the seed
     * @param tailShare share of answers drawn uniformly within {@code [minMillis, maxMillis]}
     * @param minMillis shortest answer of the tail
     * @param maxMillis longest answer of the tail
     * @param lostShare share of answers that never arrive
     * @return the model
     */
    public static LatencyModel withTail(long seed, double tailShare, int minMillis, int maxMillis, double lostShare) {
        if (tailShare < 0 || lostShare < 0 || tailShare + lostShare > 1 || minMillis < 0 || maxMillis < minMillis) {
            throw new IllegalArgumentException("tail out of range");
        }
        return new LatencyModel(seed, false, 0, tailShare, minMillis, maxMillis, lostShare);
    }

    /**
     * The measured round trip of the bench: the lognormal, 1 % between 330 and 800 ms, 0.5 % lost.
     *
     * @param seed the seed
     * @return the model
     */
    public static LatencyModel measured(long seed) {
        return withTail(seed, 0.01, 330, 800, 0.005);
    }

    /**
     * A constant latency.
     *
     * @param millis milliseconds, at least 0
     * @return the model
     */
    public static LatencyModel fixed(int millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("millis must be >= 0, was " + millis);
        }
        return new LatencyModel(0L, true, ticksOf(millis), 0, 0, 0, 0);
    }

    /**
     * Ticks of sample {@code index}. The same seed and index always return the same ticks.
     *
     * @param index sample index, also the salt's tick
     * @return ticks, at least 0, or {@value #LOST} for an answer that never arrives
     */
    public int ticks(long index) {
        if (fixed) {
            return fixedTicks;
        }
        if (tailShare > 0 || lostShare > 0) {
            var draw = unit(StableRandom.draw(seed, index, SALT_TAIL));
            if (draw < lostShare) {
                return LOST;
            }
            if (draw < lostShare + tailShare) {
                var spread = unit(StableRandom.draw(seed, index, SALT_TAIL_MILLIS));
                return ticksOf(tailMinMillis + (tailMaxMillis - tailMinMillis) * spread);
            }
        }
        return ticksOf(millis(index));
    }

    /**
     * Milliseconds of sample {@code index} of the lognormal, before the tick ceiling and without the tail.
     *
     * @param index sample index
     * @return milliseconds
     */
    public double millis(long index) {
        if (fixed) {
            return fixedTicks * 50.0;
        }
        var first = unit(StableRandom.draw(seed, index, 11));
        var second = unit(StableRandom.draw(seed, index, 12));
        var normal = StrictMath.sqrt(-2.0 * StrictMath.log(first)) * StrictMath.cos(2.0 * StrictMath.PI * second);
        var millis = StrictMath.exp(MU + SIGMA * normal);
        return millis > 60_000.0 ? 60_000.0 : millis;
    }

    private static int ticksOf(double millis) {
        return (int) StrictMath.ceil(millis / 50.0);
    }

    private static double unit(long word) {
        var bits = (word >>> 11) & ((1L << 53) - 1);
        var value = bits / (double) (1L << 53);
        return value == 0.0 ? 0x1.0p-53 : value;
    }
}
