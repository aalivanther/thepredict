package akyrion.thepredict.domain.distill;

import java.util.Arrays;

/**
 * Successive halving over stored pair noise (Y4). The noise is the probe's draw, not a new sample.
 * 3.67 rounds/s is the M22.3 target and is not a measured rate.
 */
public final class SuccessiveHalving {
    private SuccessiveHalving() {
    }

    /**
     * The surviving configuration.
     *
     * @param noise draws in generation order, one per living configuration per rung
     * @param configs configuration count
     * @param initialPairs pairs on the first rung
     * @param eta reduction factor
     * @return the winner index
     */
    public static int winner(double[] noise, int configs, int initialPairs, int eta) {
        var sums = new double[configs];
        var counts = new double[configs];
        var alive = ids(configs);
        var cursor = 0;
        var radius = initialPairs;
        while (alive.length > 1) {
            cursor = add(sums, counts, alive, noise, cursor, radius);
            alive = keep(sums, counts, alive, eta);
            radius *= eta;
        }
        return alive[0];
    }

    private static int[] ids(int configs) {
        var alive = new int[configs];
        for (var index = 0; index < configs; index++) {
            alive[index] = index;
        }
        return alive;
    }

    private static int add(double[] sums, double[] counts, int[] alive, double[] noise, int cursor, int radius) {
        for (var index : alive) {
            sums[index] += noise[cursor++];
            counts[index] += radius;
        }
        return cursor;
    }

    private static int[] keep(double[] sums, double[] counts, int[] alive, int eta) {
        var order = new Integer[alive.length];
        for (var index = 0; index < alive.length; index++) {
            order[index] = index;
        }
        Arrays.sort(order, (left, right) -> Double.compare(mean(sums, counts, alive[right]),
                mean(sums, counts, alive[left])));
        var kept = Math.max(1, alive.length / eta);
        var next = new int[kept];
        for (var index = 0; index < kept; index++) {
            next[index] = alive[order[index]];
        }
        return next;
    }

    private static double mean(double[] sums, double[] counts, int index) {
        return sums[index] / counts[index];
    }
}
