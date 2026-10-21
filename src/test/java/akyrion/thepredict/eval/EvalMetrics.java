package akyrion.thepredict.eval;

/**
 * CIF median, tactic entropy and the unwarned-hit floor (M22.1). Offline.
 */
public final class EvalMetrics {
    private EvalMetrics() {
    }

    /**
     * First time the win CIF reaches one half, or a dash when it never does.
     *
     * @param cif cumulative incidence
     * @param ticks tick of each point
     * @return the tick text, or {@code —}
     */
    public static String median(double[] cif, int[] ticks) {
        for (var index = 0; index < cif.length; index++) {
            if (cif[index] >= 0.5) {
                return Integer.toString(ticks[index]);
            }
        }
        return "—";
    }

    /**
     * Normalised Shannon entropy. One tactic is {@code 0}. Six equal tactics are {@code 1}.
     *
     * @param counts adopted tactics
     * @return the ratio
     */
    public static double entropy(int[] counts) {
        var total = 0;
        for (var count : counts) {
            total += count;
        }
        if (counts.length <= 1 || total == 0) {
            return 0.0;
        }
        var value = 0.0;
        for (var count : counts) {
            if (count == 0) {
                continue;
            }
            var p = count / (double) total;
            value -= p * StrictMath.log(p);
        }
        return value / StrictMath.log(counts.length);
    }

    /**
     * Whether a hit landed on the reaction floor, within one tick.
     *
     * @param floorTicks preset floor
     * @param hitTick ticks from the cast to the hit
     * @return whether the hit is at the floor
     */
    public static boolean unwarnedAtFloor(int floorTicks, int hitTick) {
        return Math.abs(hitTick - floorTicks) <= 1;
    }
}
