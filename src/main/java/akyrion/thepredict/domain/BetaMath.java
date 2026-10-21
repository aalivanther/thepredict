package akyrion.thepredict.domain;

/**
 * Deterministic Beta CDF and lower quantile (M26.3). Lanczos {@code g = 7} with nine coefficients and Lentz's
 * continued fraction. Callers use it when a trial closes or when inspect asks, never on every tick.
 */
public final class BetaMath {
    private static final double[] COEFFICIENTS = {
            0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
            1.5056327351493116e-7
    };
    private static final int LENTZ_LIMIT = 200;
    private static final int BISECTION_STEPS = 30;

    private BetaMath() {
    }

    /**
     * Natural log of the gamma function.
     *
     * @param z a positive argument
     * @return {@code ln Γ(z)}
     */
    public static double logGamma(double z) {
        if (z < 0.5) {
            return StrictMath.log(StrictMath.PI) - StrictMath.log(StrictMath.sin(StrictMath.PI * z))
                    - logGamma(1.0 - z);
        }
        return lanczos(z - 1.0);
    }

    /**
     * Regularized incomplete beta {@code I_x(a, b)}.
     *
     * @param x point in {@code [0, 1]}
     * @param a shape, positive
     * @param b shape, positive
     * @return the CDF
     */
    public static double cdf(double x, double a, double b) {
        if (x <= 0.0) {
            return 0.0;
        }
        if (x >= 1.0) {
            return 1.0;
        }
        var mass = StrictMath.exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * StrictMath.log(x)
                + b * StrictMath.log(1.0 - x));
        if (x < (a + 1.0) / (a + b + 2.0)) {
            return mass * fraction(a, b, x) / a;
        }
        return 1.0 - mass * fraction(b, a, 1.0 - x) / b;
    }

    /**
     * Lower quantile by thirty bisections. Used on a transition and by inspect, never per tick.
     *
     * @param q probability in {@code (0, 1)}
     * @param a shape
     * @param b shape
     * @return a point whose CDF is just below {@code q}
     */
    public static double lowerQuantile(double q, double a, double b) {
        var low = 0.0;
        var high = 1.0;
        for (var step = 0; step < BISECTION_STEPS; step++) {
            var mid = (low + high) * 0.5;
            if (cdf(mid, a, b) < q) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Whether the lower tail at {@code theta} is thinner than {@code q}.
     *
     * @param theta threshold
     * @param q tail probability
     * @param a posterior shape
     * @param b posterior shape
     * @return {@code CDF(theta) < q}
     */
    public static boolean tailBelow(double theta, double q, double a, double b) {
        return cdf(theta, a, b) < q;
    }

    private static double lanczos(double z) {
        var sum = COEFFICIENTS[0];
        for (var i = 1; i < COEFFICIENTS.length; i++) {
            sum += COEFFICIENTS[i] / (z + i);
        }
        var t = z + 7.5;
        return 0.5 * StrictMath.log(2.0 * StrictMath.PI) + (z + 0.5) * StrictMath.log(t) - t + StrictMath.log(sum);
    }

    private static double fraction(double a, double b, double x) {
        var walker = new Lentz(a, b, x);
        walker.open();
        for (var m = 1; m <= LENTZ_LIMIT && !walker.done; m++) {
            walker.pair(m);
        }
        return walker.h;
    }

    private static final class Lentz {
        private final double a;
        private final double b;
        private final double x;
        private final double qab;
        private final double qap;
        private final double qam;
        private double c = 1.0;
        private double d;
        private double h;
        private boolean done;

        private Lentz(double a, double b, double x) {
            this.a = a;
            this.b = b;
            this.x = x;
            this.qab = a + b;
            this.qap = a + 1.0;
            this.qam = a - 1.0;
        }

        private void open() {
            d = 1.0 - qab * x / qap;
            if (StrictMath.abs(d) < 1.0e-30) {
                d = 1.0e-30;
            }
            d = 1.0 / d;
            h = d;
        }

        private void pair(int m) {
            var m2 = 2 * m;
            term(m * (b - m) * x / ((qam + m2) * (a + m2)));
            if (!done) {
                term(-(a + m) * (qab + m) * x / ((a + m2) * (qap + m2)));
            }
        }

        private void term(double aa) {
            d = 1.0 + aa * d;
            if (StrictMath.abs(d) < 1.0e-30) {
                d = 1.0e-30;
            }
            c = 1.0 + aa / c;
            if (StrictMath.abs(c) < 1.0e-30) {
                c = 1.0e-30;
            }
            d = 1.0 / d;
            var delta = d * c;
            h *= delta;
            done = StrictMath.abs(delta - 1.0) < 3.0e-12;
        }
    }
}
