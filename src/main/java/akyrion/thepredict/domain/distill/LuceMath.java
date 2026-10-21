package akyrion.thepredict.domain.distill;

/**
 * Luce strengths by 50 MM iterations (M29.2, rule C23). The prior is half a label per offer. Transcendental
 * functions are not used; the update is a ratio.
 */
public final class LuceMath {
    /** Fixed MM iterations. */
    public static final int ITERATIONS = 50;
    /** Prior labels per offer. */
    public static final double PRIOR = 0.5;

    private LuceMath() {
    }

    /**
     * Fits strengths from choice sets. A tactic that is never offered stays at the prior before normalisation.
     *
     * @param sets each set lists tactic indexes offered together
     * @param chosen index chosen in that set
     * @param tactics tactic count
     * @return strengths that sum to 1
     */
    public static double[] fit(int[][] sets, int[] chosen, int tactics) {
        var strength = new double[tactics];
        for (var i = 0; i < tactics; i++) {
            strength[i] = PRIOR;
        }
        for (var iteration = 0; iteration < ITERATIONS; iteration++) {
            strength = step(sets, chosen, strength);
        }
        return normalise(strength);
    }

    /**
     * One MM step. Numerators are wins plus the prior. Denominators sum {@code 1 / set strength}.
     *
     * @param sets choice sets
     * @param chosen chosen indexes
     * @param strength current strengths
     * @return the next strengths, unnormalised
     */
    static double[] step(int[][] sets, int[] chosen, double[] strength) {
        var numerator = new double[strength.length];
        var denominator = new double[strength.length];
        for (var i = 0; i < strength.length; i++) {
            numerator[i] = PRIOR;
        }
        for (var row = 0; row < sets.length; row++) {
            add(sets[row], chosen[row], strength, numerator, denominator);
        }
        var next = new double[strength.length];
        for (var i = 0; i < strength.length; i++) {
            next[i] = denominator[i] == 0.0 ? PRIOR : numerator[i] / denominator[i];
        }
        return next;
    }

    private static void add(int[] set, int chosen, double[] strength, double[] numerator, double[] denominator) {
        var sum = 0.0;
        for (var tactic : set) {
            sum += strength[tactic];
        }
        if (sum == 0.0) {
            return;
        }
        numerator[chosen] += 1.0;
        var share = 1.0 / sum;
        for (var tactic : set) {
            denominator[tactic] += share;
        }
    }

    /**
     * Divides by the sum. A zero sum stays as equal shares.
     *
     * @param strength unnormalised strengths
     * @return strengths that sum to 1
     */
    static double[] normalise(double[] strength) {
        var sum = 0.0;
        for (var value : strength) {
            sum += value;
        }
        var out = new double[strength.length];
        if (sum == 0.0) {
            var share = 1.0 / strength.length;
            for (var i = 0; i < out.length; i++) {
                out[i] = share;
            }
            return out;
        }
        for (var i = 0; i < out.length; i++) {
            out[i] = strength[i] / sum;
        }
        return out;
    }
}
