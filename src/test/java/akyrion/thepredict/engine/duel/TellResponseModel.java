package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.TellAnswer;

/**
 * Response model v2 of X2 (M23.10). A tell of {@code T} ticks is read after packet delivery, a lognormal reaction and
 * input quantization. Success is {@code RTT + R + A + delivery + quantization <= T × 50 ms}.
 */
public final class TellResponseModel {
    /** Inverse normal of 0.9, the divisor that turns a median and a p90 into a lognormal sigma. */
    public static final double P90_Z = 1.2815515655446004;
    private static final int SALT_U1 = 41;
    private static final int SALT_U2 = 42;
    private static final int SALT_DELIVERY = 43;
    private static final int SALT_QUANT = 44;

    private TellResponseModel() {
    }

    /** Player speed used by X2. */
    public enum Player {
        /** Median 300 ms, p90 420 ms. */
        FAST(300.0, 420.0),
        /** Median 380 ms, p90 560 ms. */
        TYPICAL(380.0, 560.0),
        /** Median 480 ms, p90 760 ms. */
        SLOW(480.0, 760.0);

        private final double medianMillis;
        private final double p90Millis;

        Player(double medianMillis, double p90Millis) {
            this.medianMillis = medianMillis;
            this.p90Millis = p90Millis;
        }
    }

    /**
     * Action ticks of one answer. Block and dodge take 5. Backing off, breaking sight and interrupting take 8.
     *
     * @param answer the answer
     * @return ticks
     */
    public static int actionTicks(TellAnswer answer) {
        return switch (answer) {
            case BLOCK, DODGE -> 5;
            case BACK_OFF, BREAK_SIGHT, INTERRUPT -> 8;
        };
    }

    /**
     * One v2 sample.
     *
     * @param telegraphTicks ticks shown
     * @param rttMillis round trip
     * @param player speed
     * @param actionTicks ticks until the action counts
     * @param seed draw seed
     * @param sample sample index
     * @return whether the player made it
     */
    public static boolean succeeds(int telegraphTicks, double rttMillis, Player player, int actionTicks, long seed,
                                   long sample) {
        return needMillis(rttMillis, player, actionTicks, seed, sample, true) <= telegraphTicks * 50.0;
    }

    /**
     * Share of samples that succeed.
     *
     * @param telegraphTicks ticks shown
     * @param rttMillis round trip
     * @param player speed
     * @param actionTicks ticks until the action counts
     * @param samples how many draws
     * @param seed draw seed
     * @param delivery whether v2 adds delivery and quantization
     * @return the share
     */
    public static double rate(int telegraphTicks, double rttMillis, Player player, int actionTicks, int samples,
                              long seed, boolean delivery) {
        var hits = 0;
        for (var sample = 0; sample < samples; sample++) {
            if (needMillis(rttMillis, player, actionTicks, seed, sample, delivery) <= telegraphTicks * 50.0) {
                hits++;
            }
        }
        return samples == 0 ? 0.0 : hits / (double) samples;
    }

    private static double needMillis(double rttMillis, Player player, int actionTicks, long seed, long sample,
                                     boolean delivery) {
        var reaction = lognormalMillis(player, seed, sample);
        var extra = delivery ? 50.0 * (unit(seed, sample, SALT_DELIVERY) + unit(seed, sample, SALT_QUANT)) : 0.0;
        return rttMillis + reaction + actionTicks * 50.0 + extra;
    }

    private static double lognormalMillis(Player player, long seed, long sample) {
        var sigma = StrictMath.log(player.p90Millis / player.medianMillis) / P90_Z;
        var mu = StrictMath.log(player.medianMillis);
        var z = StrictMath.sqrt(-2.0 * StrictMath.log(unit(seed, sample, SALT_U1)))
                * StrictMath.cos(2.0 * StrictMath.PI * unit(seed, sample, SALT_U2));
        return StrictMath.exp(mu + sigma * z);
    }

    private static double unit(long seed, long sample, int salt) {
        var word = StableRandom.draw(seed, sample, salt);
        var drawn = (word >>> 11) * 0x1.0p-53;
        return drawn <= 0.0 ? 0x1.0p-53 : drawn;
    }
}
