package akyrion.thepredict.engine;

import java.util.Arrays;
import java.util.OptionalLong;

/**
 * Latency histogram of one span with fixed buckets in microseconds (M09.6). Two histograms alternate every minute,
 * the current one and the previous one, so percentiles cover the last one to two minutes with fixed memory. The
 * maximum and the sample count cover the whole run. Server thread only.
 */
final class LatencyWindow {
    /** Upper bounds of the buckets in microseconds; one more bucket holds everything above the last bound. */
    static final long[] BOUNDS_MICROS = {50, 100, 250, 500, 1_000, 2_000, 5_000, 10_000, 25_000, 50_000, 100_000,
            200_000, 300_000, 500_000, 800_000, 1_200_000, 2_000_000};

    private static final long MINUTE_NANOS = 60_000_000_000L;
    private static final long NO_MINUTE = Long.MIN_VALUE;

    private final long[] current = new long[BOUNDS_MICROS.length + 1];
    private final long[] previous = new long[BOUNDS_MICROS.length + 1];
    private long minute = NO_MINUTE;
    private long samples;
    private long maxMicros;

    /** Adds one sample; a negative duration counts as zero. */
    void record(long elapsedNanos, long nowNanos) {
        roll(nowNanos);
        var micros = Math.max(0, elapsedNanos) / 1_000;
        current[bucket(micros)]++;
        samples++;
        maxMicros = Math.max(maxMicros, micros);
    }

    /**
     * Returns the upper bound of the bucket that holds the given rank of the current and previous minute, read by
     * cumulative count; {@link Long#MAX_VALUE} for the open bucket above two seconds.
     */
    OptionalLong percentileMicros(double ratio, long nowNanos) {
        if (!(ratio > 0.0 && ratio <= 1.0)) {
            throw new IllegalArgumentException("ratio must be within (0, 1], was " + ratio);
        }
        roll(nowNanos);
        var total = Arrays.stream(current).sum() + Arrays.stream(previous).sum();
        if (total == 0) {
            return OptionalLong.empty();
        }
        var rank = Math.max(1, (long) Math.ceil(ratio * total));
        var cumulative = 0L;
        for (var index = 0; index < current.length; index++) {
            cumulative += current[index] + previous[index];
            if (cumulative >= rank) {
                return OptionalLong.of(index < BOUNDS_MICROS.length ? BOUNDS_MICROS[index] : Long.MAX_VALUE);
            }
        }
        return OptionalLong.of(Long.MAX_VALUE);
    }

    /** Counts every sample since creation. */
    long samples() {
        return samples;
    }

    /** Returns the longest sample since creation, in microseconds. */
    long maxMicros() {
        return maxMicros;
    }

    /** Moves the current minute to the previous one, or clears both after a gap of more than one minute. */
    private void roll(long nowNanos) {
        var now = Math.floorDiv(nowNanos, MINUTE_NANOS);
        if (minute != NO_MINUTE && now <= minute) {
            return;
        }
        if (minute != NO_MINUTE && now == minute + 1) {
            System.arraycopy(current, 0, previous, 0, current.length);
        } else {
            Arrays.fill(previous, 0);
        }
        Arrays.fill(current, 0);
        minute = now;
    }

    private static int bucket(long micros) {
        for (var index = 0; index < BOUNDS_MICROS.length; index++) {
            if (micros <= BOUNDS_MICROS[index]) {
                return index;
            }
        }
        return BOUNDS_MICROS.length;
    }
}
