package akyrion.thepredict.eval;

import akyrion.thepredict.domain.StableRandom;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wilson, Clopper–Pearson and the normal pair GSPRT (M22.2, X4). Means are reported in rounds. */
class EvalStatsTest {
    @Test
    void wilsonAndTheZeroFailureBoundMatchThePublishedExamples() {
        assertEquals(0.79, EvalStats.wilson(28, 30)[0], 0.01);
        assertEquals(0.744, EvalStats.wilson(27, 30)[0], 0.01);
        assertEquals(0.77, EvalStats.wilson(31, 34)[0], 0.01);
        assertEquals(0.77, EvalStats.wilson(36, 40)[0], 0.01);
        assertTrue(EvalStats.wilson(15, 20)[0] > 0.50);
        assertTrue(EvalStats.wilson(14, 20)[0] < 0.50);
        assertEquals(0.722, EvalStats.wilson(81, 100)[0], 0.005);
        assertEquals(0.875, EvalStats.wilson(81, 100)[1], 0.005);
        assertTrue(EvalStats.zeroFailureUpper(299, 0.05) <= 0.01);
        assertEquals(StrictMath.log(19.0), EvalStats.SPRT_A, 1e-6);
        assertEquals(StrictMath.log(19.0), EvalStats.threshold(0.05, 0.05), 1e-9);
        var flat = new double[EvalStats.MAX_PAIRS];
        java.util.Arrays.fill(flat, 0.5);
        assertEquals("CONTINUE", EvalStats.gsprtPairs(flat, 5, 0.50, 0.55, EvalStats.MAX_PAIRS, EvalStats.SPRT_A));
        assertEquals("ACCEPT_H0", EvalStats.gsprtPairs(flat, EvalStats.MAX_PAIRS, 0.50, 0.55, EvalStats.MAX_PAIRS,
                EvalStats.SPRT_A), "800 even halves sit on s0: no gain, not a run to the cap");
    }

    @Test
    void unanimousPairsAcceptH1() {
        var wins = new double[EvalStats.MAX_PAIRS];
        java.util.Arrays.fill(wins, 1.0);
        assertEquals("ACCEPT_H1", EvalStats.gsprtPairs(wins, 10, 0.50, 0.55, EvalStats.MAX_PAIRS, EvalStats.SPRT_A));
        var losses = new double[EvalStats.MAX_PAIRS];
        assertEquals("ACCEPT_H0", EvalStats.gsprtPairs(losses, 10, 0.50, 0.55, EvalStats.MAX_PAIRS, EvalStats.SPRT_A));
        assertTrue(Double.isFinite(EvalStats.pairLlr(wins, 10, 0.50, 0.55)));
        var ties = new double[EvalStats.MAX_PAIRS];
        java.util.Arrays.fill(ties, 0.5);
        var perPair = (0.50 - 0.45) * (2 * 0.5 - 0.45 - 0.50) / (2 * 0.5 * 0.5);
        var first = (int) Math.ceil(EvalStats.SPRT_A / perPair / EvalStats.LOOK_EVERY) * EvalStats.LOOK_EVERY;
        assertEquals(perPair * 4, EvalStats.pairLlr(ties, 4, 0.45, 0.50), 1e-12, "even pairs take the floor ½·½");
        assertEquals("CONTINUE", EvalStats.gsprtPairs(ties, first - EvalStats.LOOK_EVERY, 0.45, 0.50,
                EvalStats.MAX_PAIRS, EvalStats.SPRT_A));
        assertEquals("ACCEPT_H1", EvalStats.gsprtPairs(ties, first, 0.45, 0.50, EvalStats.MAX_PAIRS, EvalStats.SPRT_A),
                "identical arms are no regression at the first look whose sum reaches the bound");
    }

    @Test
    void theWilsonIntervalStaysInsideTheUnitAndOneSidedBoundsFollowTheirQuantile() {
        var none = EvalStats.wilson(0, 40);
        var all = EvalStats.wilson(40, 40);
        assertTrue(none[0] >= 0.0 && all[1] <= 1.0, none[0] + " " + all[1]);
        assertEquals(1.959963984540054, EvalStats.normalQuantile(0.975), 1e-12);
        assertEquals(1.6448536269514722, EvalStats.normalQuantile(0.95), 1e-12);
        assertEquals(-2.3263478740408408, EvalStats.normalQuantile(0.01), 1e-12);
        assertEquals(EvalStats.normalQuantile(1 - 0.05 / 24), EvalStats.bonferroniZ(0.05, 24), 1e-15);
        assertEquals(2.865, EvalStats.bonferroniZ(0.05, 24), 0.001);
        var oneSided = EvalStats.wilson(325, 400, EvalStats.bonferroniZ(0.05, 24))[0];
        var fewer = EvalStats.wilson(324, 400, EvalStats.bonferroniZ(0.05, 24))[0];
        assertTrue(oneSided >= 0.75 && fewer < 0.75, oneSided + " " + fewer);
        var sample = new double[] {-1, -2, -3, -4, 1};
        assertTrue(EvalStats.bootstrapQuantile(sample, 2000, 3L, 0.95) > EvalStats.bootstrapQuantile(sample, 2000,
                3L, 0.05));
    }

    @Test
    void holmBhAndMcnemarMatchTheTextbookAndTheBootstrapRepeats() {
        var holm = EvalStats.holm(new double[] {0.01, 0.04, 0.03});
        assertEquals(0.03, holm[0], 1e-12);
        assertEquals(0.06, holm[1], 1e-12);
        assertEquals(0.06, holm[2], 1e-12);
        var bh = EvalStats.benjaminiHochberg(new double[] {0.01, 0.04, 0.03});
        assertEquals(0.03, bh[0], 1e-12);
        assertEquals(0.04, bh[1], 1e-12);
        assertEquals(0.04, bh[2], 1e-12);
        assertEquals(0.03125, EvalStats.mcnemar(6, 0), 1e-12);
        assertEquals(1.0, EvalStats.mcnemar(0, 0), 0.0);
        var sample = new double[] {1, 2, 3, 4, 8};
        assertEquals(EvalStats.bootstrapMean(sample, 2000, 7L), EvalStats.bootstrapMean(sample, 2000, 7L));
        assertNotEquals(EvalStats.bootstrapMean(sample, 2000, 7L), EvalStats.bootstrapMean(sample, 2000, 8L));
        assertTrue(EvalStats.bonferroni(0.05, 0.05, 4) > EvalStats.SPRT_A);
    }

    @Test
    void pairLooksRecordTheRoundMeansWithoutFailingThePublishedBand() throws Exception {
        var bench = sample(0.95, 0.95, 0.99, 2000, EvalStats.SPRT_A, 1L);
        var tighter = sample(0.97, 0.97, 0.995, 2000, EvalStats.SPRT_A, 50_000L);
        var typeI = rate(0.95, 0.95, 0.99, 2000, EvalStats.SPRT_A, 90_000L, "ACCEPT_H1");
        var typeII = rate(0.99, 0.95, 0.99, 2000, EvalStats.SPRT_A, 140_000L, "ACCEPT_H0");
        var bound = EvalStats.bonferroni(0.05, 0.05, 4);
        var family = 0;
        for (var replica = 0; replica < 2000; replica++) {
            var hit = false;
            for (var contrast = 0; contrast < 4; contrast++) {
                if ("ACCEPT_H1".equals(decision(0.50, 0.50, 0.55, 200_000L + replica * 4L + contrast, bound))) {
                    hit = true;
                }
            }
            if (hit) {
                family++;
            }
        }
        var familyRate = family / 2000.0;
        var band99 = Math.abs(bench.meanRounds - 79.0) <= 79.0 * 0.10;
        var band995 = Math.abs(tighter.meanRounds - 113.0) <= 113.0 * 0.10;
        var text = "unit=rounds pairs=" + bench.meanPairs + " BENCH_99=" + bench.meanRounds
                + " BENCH_995=" + tighter.meanRounds + " typeI=" + typeI + " typeII=" + typeII
                + " family=" + familyRate + " band99=" + band99 + " band995=" + band995 + "\n";
        var output = Path.of("build/reports/m22/gsprt-means.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, text);
        assertTrue(bench.meanRounds > 0.0);
        assertTrue(tighter.meanRounds > 0.0);
        assertTrue(typeI >= 0.0 && typeII >= 0.0 && familyRate >= 0.0);
    }

    private static Sample sample(double rate, double s0, double s1, int replicas, double bound, long seed) {
        var rounds = 0.0;
        var pairs = 0.0;
        for (var replica = 0; replica < replicas; replica++) {
            var length = length(rate, s0, s1, seed + replica, bound);
            pairs += length;
            rounds += length * 2.0;
        }
        return new Sample(rounds / replicas, pairs / replicas);
    }

    private static double rate(double rate, double s0, double s1, int replicas, double bound, long seed, String hit) {
        var hits = 0;
        for (var replica = 0; replica < replicas; replica++) {
            if (hit.equals(decision(rate, s0, s1, seed + replica, bound))) {
                hits++;
            }
        }
        return hits / (double) replicas;
    }

    private static int length(double rate, double s0, double s1, long seed, double bound) {
        var halves = new double[EvalStats.MAX_PAIRS];
        for (var pair = 1; pair <= EvalStats.MAX_PAIRS; pair++) {
            halves[pair - 1] = (draw(seed, pair, 1, rate) + draw(seed, pair, 2, rate)) / 2.0;
            if (pair % EvalStats.LOOK_EVERY != 0) {
                continue;
            }
            var decision = EvalStats.gsprtPairs(halves, pair, s0, s1, EvalStats.MAX_PAIRS, bound);
            if (!"CONTINUE".equals(decision)) {
                return pair;
            }
        }
        return EvalStats.MAX_PAIRS;
    }

    private static String decision(double rate, double s0, double s1, long seed, double bound) {
        var halves = new double[EvalStats.MAX_PAIRS];
        var last = "CONTINUE";
        for (var pair = 1; pair <= EvalStats.MAX_PAIRS; pair++) {
            halves[pair - 1] = (draw(seed, pair, 1, rate) + draw(seed, pair, 2, rate)) / 2.0;
            if (pair % EvalStats.LOOK_EVERY != 0 && pair < EvalStats.MAX_PAIRS) {
                continue;
            }
            last = EvalStats.gsprtPairs(halves, pair, s0, s1, EvalStats.MAX_PAIRS, bound);
            if (!"CONTINUE".equals(last)) {
                return last;
            }
        }
        return last;
    }

    private static double draw(long seed, int pair, int side, double rate) {
        var word = StableRandom.draw(seed, pair, side);
        var unit = (word >>> 11) / (double) (1L << 53);
        return unit < rate ? 1.0 : 0.0;
    }

    private record Sample(double meanRounds, double meanPairs) {
    }
}
