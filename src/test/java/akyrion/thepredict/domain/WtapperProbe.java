package akyrion.thepredict.domain;

/**
 * X1 {@code w_tapper} on the production predictor (M26.3). The draw matches CPython {@code random.Random}. The
 * predictor is order at most 2 and has no order-0 table, so the published probe figure is a comparison, not a
 * target the tables are tuned toward.
 */
final class WtapperProbe {
    private static final int TOKENS = 16;
    private static final int N = 624;
    private static final int M = 397;
    private static final int IDLE = 15;

    private WtapperProbe() {
    }

    static double accuracy() {
        var hits = 0;
        var total = 0;
        for (var seed = 0; seed < 200; seed++) {
            var rng = new PyRandom(1000 * seed + 7);
            var sequence = generate(rng);
            var predictor = new ActionPredictor(64);
            for (var action : sequence) {
                var predicted = predictor.predict();
                if (predicted == action) {
                    hits++;
                }
                total++;
                predictor.update(action, predicted, 1.0);
            }
        }
        return hits / (double) total;
    }

    private static int[] generate(PyRandom rng) {
        var sequence = new int[400];
        var previous = IDLE;
        for (var index = 0; index < sequence.length; index++) {
            previous = sample(previous, rng);
            sequence[index] = previous;
        }
        return sequence;
    }

    private static int sample(int previous, PyRandom rng) {
        if (rng.nextDouble() < 0.15) {
            return rng.below(TOKENS);
        }
        var options = row(previous);
        var total = 0;
        for (var option : options) {
            total += option[1];
        }
        var draw = rng.nextDouble() * total;
        for (var option : options) {
            draw -= option[1];
            if (draw < 0) {
                return option[0];
            }
        }
        return options[options.length - 1][0];
    }

    private static int[][] row(int previous) {
        if (previous == 0) {
            return new int[][] {{10, 7}, {5, 1}, {6, 1}, {8, 1}};
        }
        if (previous == 10) {
            return new int[][] {{7, 8}, {9, 1}, {IDLE, 1}};
        }
        if (previous == 7) {
            return new int[][] {{0, 8}, {2, 1}, {IDLE, 1}};
        }
        return new int[][] {{7, 2}, {5, 1}, {6, 1}, {0, 2}, {IDLE, 1}};
    }

    static final class PyRandom {
        private final int[] mt = new int[N];
        private int index = N;

        PyRandom(int seed) {
            mt[0] = 19650218;
            for (var step = 1; step < N; step++) {
                mt[step] = 1812433253 * (mt[step - 1] ^ (mt[step - 1] >>> 30)) + step;
            }
            var i = 1;
            var j = 0;
            var key = seed;
            for (var k = N; k > 0; k--) {
                mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525)) + key + j;
                i++;
                j++;
                if (i >= N) {
                    mt[0] = mt[N - 1];
                    i = 1;
                }
                if (j >= 1) {
                    j = 0;
                }
            }
            for (var k = N - 1; k > 0; k--) {
                mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941)) - i;
                i++;
                if (i >= N) {
                    mt[0] = mt[N - 1];
                    i = 1;
                }
            }
            mt[0] = 0x80000000;
        }

        double nextDouble() {
            var a = (next() >>> 5) & 0x7ffffff;
            var b = (next() >>> 6) & 0x3ffffff;
            return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
        }

        int below(int bound) {
            var bits = 32 - Integer.numberOfLeadingZeros(bound);
            var draw = next() >>> (32 - bits);
            while (draw >= bound) {
                draw = next() >>> (32 - bits);
            }
            return draw;
        }

        private int next() {
            if (index >= N) {
                twist();
            }
            var y = mt[index++];
            y ^= y >>> 11;
            y ^= (y << 7) & 0x9d2c5680;
            y ^= (y << 15) & 0xefc60000;
            y ^= y >>> 18;
            return y;
        }

        private void twist() {
            for (var step = 0; step < N; step++) {
                var y = (mt[step] & 0x80000000) | (mt[(step + 1) % N] & 0x7fffffff);
                mt[step] = mt[(step + M) % N] ^ (y >>> 1) ^ ((y & 1) == 0 ? 0 : 0x9908b0df);
            }
            index = 0;
        }
    }
}
