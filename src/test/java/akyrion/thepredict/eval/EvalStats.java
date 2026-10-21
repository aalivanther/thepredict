package akyrion.thepredict.eval;

import akyrion.thepredict.domain.StableRandom;

/**
 * Wilson, Clopper–Pearson, the normal GSPRT, bootstrap, McNemar, Holm and Benjamini–Hochberg (M22.2). Offline.
 * Transcendentals go through {@link StrictMath}. Bonferroni uses {@code α/k}. The only Wilson interval of the
 * evaluation and of the duel simulator lives here, cut to {@code [0, 1]} (SIM-20).
 */
public final class EvalStats {
    /** Two-sided 95 % normal quantile. */
    public static final double Z95 = 1.959963984540054;
    /** SPRT threshold at {@code α = β = 0.05}. */
    public static final double SPRT_A = 2.944438879;
    /** Pairs between looks. */
    public static final int LOOK_EVERY = 10;
    /** Truncation of {@code GAIN} and {@code NON_REGRESSION}. */
    public static final int MAX_PAIRS = 800;

    private EvalStats() {
    }

    /**
     * Wilson interval at 95 %, two-sided, cut to {@code [0, 1]}.
     *
     * @param successes successes
     * @param trials trials
     * @return low and high
     */
    public static double[] wilson(double successes, int trials) {
        return wilson(successes, trials, Z95);
    }

    /**
     * Wilson interval for a normal quantile {@code z}, cut to {@code [0, 1]}. A draw may count half a success: the
     * variance of {@code {0, ½, 1}} is at most the Bernoulli one, so the interval stays conservative.
     *
     * @param successes successes
     * @param trials trials, at least 1
     * @param z the normal quantile, {@code 1.645} for one side at 95 %
     * @return low and high
     */
    public static double[] wilson(double successes, int trials, double z) {
        var p = successes / trials;
        var z2 = z * z;
        var denom = 1.0 + z2 / trials;
        var center = (p + z2 / (2.0 * trials)) / denom;
        var margin = z * StrictMath.sqrt(p * (1.0 - p) / trials + z2 / (4.0 * trials * trials)) / denom;
        return new double[] {Math.max(0.0, center - margin), Math.min(1.0, center + margin)};
    }

    /**
     * The normal quantile: the {@code z} with {@code Φ(z) = p} (Wichura, AS 241, about 16 digits).
     *
     * @param p a probability in {@code (0, 1)}
     * @return the quantile
     */
    public static double normalQuantile(double p) {
        if (!(p > 0.0 && p < 1.0)) {
            throw new IllegalArgumentException("p must be within (0, 1), was " + p);
        }
        var q = p - 0.5;
        if (Math.abs(q) <= 0.425) {
            var r = 0.180625 - q * q;
            return q * central(r);
        }
        var r = StrictMath.sqrt(-StrictMath.log(q < 0 ? p : 1.0 - p));
        var value = r <= 5.0 ? near(r - 1.6) : far(r - 5.0);
        return q < 0 ? -value : value;
    }

    private static double central(double r) {
        return (((((((r * 2509.0809287301226727 + 33430.575583588128105) * r + 67265.770927008700853) * r
                + 45921.953931549871457) * r + 13731.693765509461125) * r + 1971.5909503065514427) * r
                + 133.14166789178437745) * r + 3.387132872796366608)
                / (((((((r * 5226.495278852545925 + 28729.085735721942674) * r + 39307.89580009271061) * r
                + 21213.794301586595867) * r + 5394.1960214247511077) * r + 687.1870074920579083) * r
                + 42.313330701600911252) * r + 1.0);
    }

    private static double near(double r) {
        return (((((((r * 7.7454501427834140764e-4 + 0.0227238449892691845833) * r + 0.24178072517745061177) * r
                + 1.27045825245236838258) * r + 3.64784832476320460504) * r + 5.7694972214606914055) * r
                + 4.6303378461565452959) * r + 1.42343711074968357734)
                / (((((((r * 1.05075007164441684324e-9 + 5.475938084995344946e-4) * r + 0.0151986665636164571966) * r
                + 0.14810397642748007459) * r + 0.68976733498510000455) * r + 1.6763848301838038494) * r
                + 2.05319162663775882187) * r + 1.0);
    }

    private static double far(double r) {
        return (((((((r * 2.01033439929228813265e-7 + 2.71155556874348757815e-5) * r + 0.0012426609473880784386) * r
                + 0.026532189526576123093) * r + 0.29656057182850489123) * r + 1.7848265399172913358) * r
                + 5.4637849111641143699) * r + 6.6579046435011037772)
                / (((((((r * 2.04426310338993978564e-15 + 1.4215117583164458887e-7) * r + 1.8463183175100546818e-5) * r
                + 7.868691311456132591e-4) * r + 0.0148753612908506148525) * r + 0.13692988092273580531) * r
                + 0.59983220655588793769) * r + 1.0);
    }

    /**
     * The one-sided normal quantile of a family of {@code k} tests at level {@code alpha} (Bonferroni).
     *
     * @param alpha the family level
     * @param contrasts how many tests
     * @return {@code Φ⁻¹(1 − α/k)}
     */
    public static double bonferroniZ(double alpha, int contrasts) {
        return normalQuantile(1.0 - alpha / contrasts);
    }

    /**
     * Upper failure rate when every trial succeeded, {@code 1 − α^(1/n)}.
     *
     * @param trials trials
     * @param alpha the tail
     * @return the bound
     */
    public static double zeroFailureUpper(int trials, double alpha) {
        return 1.0 - StrictMath.pow(alpha, 1.0 / trials);
    }

    /**
     * One normal GSPRT look on the first {@code n} pair halves. A pair half is {@code pair score / 2}. The look runs
     * only every {@link #LOOK_EVERY} pairs. {@code LLR = n·(s1−s0)·(2ŝ−s0−s1)/(2σ̂²)} with {@code σ̂²} the sample
     * variance. Truncation is {@code INCONCLUSIVE}.
     *
     * @param halves pair score divided by two, at least {@code n} entries
     * @param n pairs seen
     * @param s0 null mean of a pair half
     * @param s1 alternative mean of a pair half
     * @param maxPairs the cap
     * @param bound {@code A = ln((1−β)/α)}; H0 uses {@code −bound}
     * @return {@code ACCEPT_H1}, {@code ACCEPT_H0}, {@code INCONCLUSIVE} or {@code CONTINUE}
     */
    public static String gsprtPairs(double[] halves, int n, double s0, double s1, int maxPairs, double bound) {
        if (n < 2 || (n % LOOK_EVERY != 0 && n < maxPairs)) {
            return "CONTINUE";
        }
        if (n % LOOK_EVERY != 0) {
            return "INCONCLUSIVE";
        }
        var llr = pairLlr(halves, n, s0, s1);
        if (Double.isNaN(llr)) {
            return n >= maxPairs ? "INCONCLUSIVE" : "CONTINUE";
        }
        if (llr >= bound) {
            return "ACCEPT_H1";
        }
        if (llr <= -bound) {
            return "ACCEPT_H0";
        }
        return n >= maxPairs ? "INCONCLUSIVE" : "CONTINUE";
    }

    /**
     * {@code LLR = n·(s1−s0)·(2ŝ−s0−s1)/(2σ̂²)} on the first {@code n} pair halves. {@code σ̂²} is the sample
     * variance; a unanimous sample, whose variance is 0, takes the Bernoulli floor {@code ŝ(1−ŝ)} with {@code ŝ} cut to
     * {@code [1/(2n), 1−1/(2n)]}, so it decides instead of running to the cap (SIM-15). The result is
     * {@link Double#NaN} only below two pairs.
     *
     * @param halves pair score divided by two, at least {@code n} entries
     * @param n pairs seen
     * @param s0 null mean of a pair half
     * @param s1 alternative mean of a pair half
     * @return the log-likelihood ratio, or {@link Double#NaN}
     */
    public static double pairLlr(double[] halves, int n, double s0, double s1) {
        if (n < 2) {
            return Double.NaN;
        }
        var mean = 0.0;
        for (var index = 0; index < n; index++) {
            mean += halves[index];
        }
        mean /= n;
        var variance = 0.0;
        for (var index = 0; index < n; index++) {
            var delta = halves[index] - mean;
            variance += delta * delta;
        }
        variance /= n - 1.0;
        if (!(variance > 0.0)) {
            var cut = Math.max(1.0 / (2.0 * n), Math.min(1.0 - 1.0 / (2.0 * n), mean));
            variance = cut * (1.0 - cut);
        }
        return n * (s1 - s0) * (2.0 * mean - s0 - s1) / (2.0 * variance);
    }

    /**
     * SPRT boundary {@code ln((1 − β) / α)}. Bonferroni passes {@code α / k}.
     *
     * @param alpha the type I budget
     * @param beta the type II budget
     * @return the boundary
     */
    public static double threshold(double alpha, double beta) {
        return StrictMath.log((1.0 - beta) / alpha);
    }

    /**
     * Bonferroni threshold for {@code k} simultaneous SPRTs.
     *
     * @param alpha family type I budget
     * @param beta type II budget of each contrast
     * @param contrasts how many contrasts
     * @return {@code ln((1−β)/(α/k))}
     */
    public static double bonferroni(double alpha, double beta, int contrasts) {
        return threshold(alpha / contrasts, beta);
    }

    /**
     * Elo difference {@code 400 · log10(wins/losses)} with the classic anchor at 0. A record without wins or without
     * losses takes half a game on both sides (Haldane), so the Elo stays finite (SIM-16).
     *
     * @param wins wins of the rival
     * @param losses losses of the rival
     * @return the Elo
     */
    public static double elo(int wins, int losses) {
        if (wins == 0 || losses == 0) {
            return 400.0 * StrictMath.log10((wins + 0.5) / (losses + 0.5));
        }
        return 400.0 * StrictMath.log10(wins / (double) losses);
    }

    /**
     * A paired bootstrap mean. The draw uses {@link StableRandom}.
     *
     * @param values the sample
     * @param draws resamples
     * @param seed the seed
     * @return the mean of the resample means
     */
    public static double bootstrapMean(double[] values, int draws, long seed) {
        var total = 0.0;
        for (var draw = 0; draw < draws; draw++) {
            var sum = 0.0;
            for (var index = 0; index < values.length; index++) {
                var pick = (int) Long.remainderUnsigned(StableRandom.draw(seed, draw, index), values.length);
                sum += values[pick];
            }
            total += sum / values.length;
        }
        return total / draws;
    }

    /**
     * A percentile of the bootstrap means. The draw uses {@link StableRandom}.
     *
     * @param values the sample, at least one value
     * @param draws resamples
     * @param seed the seed
     * @param quantile the percentile as a fraction, {@code 0.95} for the upper bound of a one-sided 95 %
     * @return that percentile of the resample means
     */
    public static double bootstrapQuantile(double[] values, int draws, long seed, double quantile) {
        var means = new double[draws];
        for (var draw = 0; draw < draws; draw++) {
            var sum = 0.0;
            for (var index = 0; index < values.length; index++) {
                var pick = (int) Long.remainderUnsigned(StableRandom.draw(seed, draw, index), values.length);
                sum += values[pick];
            }
            means[draw] = sum / values.length;
        }
        java.util.Arrays.sort(means);
        return means[Math.min(draws - 1, (int) StrictMath.floor(quantile * draws))];
    }

    /**
     * Two-sided exact McNemar p-value on the discordant counts.
     *
     * @param firstOnly successes only on the first arm
     * @param secondOnly successes only on the second arm
     * @return the p-value
     */
    public static double mcnemar(int firstOnly, int secondOnly) {
        var n = firstOnly + secondOnly;
        if (n == 0) {
            return 1.0;
        }
        var k = Math.min(firstOnly, secondOnly);
        var tail = 0.0;
        for (var index = 0; index <= k; index++) {
            tail += binomial(n, index) * StrictMath.pow(0.5, n);
        }
        return Math.min(1.0, 2.0 * tail);
    }

    /**
     * Holm adjusted p-values, in the input order.
     *
     * @param p raw p-values
     * @return adjusted p-values
     */
    public static double[] holm(double[] p) {
        var order = order(p);
        var adjusted = new double[p.length];
        var running = 0.0;
        for (var rank = 0; rank < order.length; rank++) {
            var value = Math.min(1.0, (p.length - rank) * p[order[rank]]);
            running = Math.max(running, value);
            adjusted[order[rank]] = running;
        }
        return adjusted;
    }

    /**
     * Benjamini–Hochberg q-values, in the input order.
     *
     * @param p raw p-values
     * @return q-values
     */
    public static double[] benjaminiHochberg(double[] p) {
        var order = order(p);
        var adjusted = new double[p.length];
        var running = 1.0;
        for (var rank = order.length - 1; rank >= 0; rank--) {
            var value = Math.min(1.0, p[order[rank]] * p.length / (rank + 1.0));
            running = Math.min(running, value);
            adjusted[order[rank]] = running;
        }
        return adjusted;
    }

    private static int[] order(double[] p) {
        var order = new int[p.length];
        for (var index = 0; index < p.length; index++) {
            order[index] = index;
        }
        for (var left = 0; left < order.length; left++) {
            for (var right = left + 1; right < order.length; right++) {
                if (p[order[right]] < p[order[left]]) {
                    var swap = order[left];
                    order[left] = order[right];
                    order[right] = swap;
                }
            }
        }
        return order;
    }

    private static double binomial(int n, int k) {
        var value = 1.0;
        for (var index = 1; index <= k; index++) {
            value = value * (n - k + index) / index;
        }
        return value;
    }
}
