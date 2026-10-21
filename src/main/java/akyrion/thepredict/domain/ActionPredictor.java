package akyrion.thepredict.domain;

/**
 * Variable-order predictor of at most order 2 (M26.3). Order 2 is used when its row totals at least {@code minCount};
 * otherwise order 1; otherwise {@link EnemyAction#IDLE}. There is no order-0 table. Ties take the lowest ordinal.
 * Forgetting multiplies the updated row before the increment. Eviction reuses the lightest order-2 row, so its mass
 * carries into the new context. {@link #predict} and {@link #update} allocate nothing.
 */
public final class ActionPredictor {
    /** Minimum row total before that order is trusted. */
    public static final double MIN_COUNT = 2.0;
    /** Smoothing of the running accuracy. */
    public static final double ACCURACY_ALPHA = 0.05;

    private final int capacity;
    private final float[][] order1 = new float[EnemyAction.COUNT][EnemyAction.COUNT];
    private final int[] contextKey;
    private final float[][] order2;
    private int previous = -1;
    private int before = -1;
    private int evictions;
    private double accuracy;

    /**
     * A predictor with a bounded order-2 table.
     *
     * @param order2Contexts contexts kept, 0 to 64
     */
    public ActionPredictor(int order2Contexts) {
        if (order2Contexts < 0 || order2Contexts > 64) {
            throw new IllegalArgumentException("order2Contexts must be within [0, 64], was " + order2Contexts);
        }
        this.capacity = order2Contexts;
        this.contextKey = new int[order2Contexts];
        this.order2 = new float[order2Contexts][EnemyAction.COUNT];
        for (var slot = 0; slot < order2Contexts; slot++) {
            contextKey[slot] = -1;
        }
    }

    /**
     * The action this history predicts. Call it before {@link #update}.
     *
     * @return the ordinal of the prediction
     */
    public int predict() {
        if (previous >= 0 && before >= 0) {
            var slot = find(key(before, previous));
            if (slot >= 0 && total(order2[slot]) >= MIN_COUNT) {
                return argMax(order2[slot]);
            }
        }
        if (previous >= 0 && total(order1[previous]) >= MIN_COUNT) {
            return argMax(order1[previous]);
        }
        return EnemyAction.IDLE.ordinal();
    }

    /**
     * Learns one action and refreshes the running accuracy against the prediction just made.
     *
     * @param action the observed action
     * @param predicted the value {@link #predict} returned
     * @param forgetting row decay in {@code (0, 1]}
     */
    public void update(int action, int predicted, double forgetting) {
        var hit = action == predicted ? 1.0 : 0.0;
        accuracy = (1.0 - ACCURACY_ALPHA) * accuracy + ACCURACY_ALPHA * hit;
        if (previous >= 0) {
            decayAndAdd(order1[previous], action, forgetting);
        }
        if (previous >= 0 && before >= 0 && capacity > 0) {
            learnOrder2(key(before, previous), action, forgetting);
        }
        before = previous;
        previous = action;
    }

    /**
     * Running accuracy, an exponential average, not the online hit rate of a probe.
     *
     * @return the average in {@code [0, 1]}
     */
    public double accuracy() {
        return accuracy;
    }

    /**
     * Order-2 contexts that were dropped.
     *
     * @return the eviction count
     */
    public int evictions() {
        return evictions;
    }

    /**
     * A forecast when the accuracy and the probability both clear their floors.
     *
     * @param minAccuracy minimum running accuracy
     * @return the action and its probability in permille, or a zero permille when it is not used
     */
    public Forecast forecast(double minAccuracy) {
        if (accuracy < minAccuracy || previous < 0) {
            return Forecast.NONE;
        }
        var row = order1[previous];
        if (before >= 0) {
            var slot = find(key(before, previous));
            if (slot >= 0 && total(order2[slot]) >= MIN_COUNT) {
                row = order2[slot];
            }
        }
        var mass = total(row);
        if (mass < MIN_COUNT) {
            return Forecast.NONE;
        }
        var choice = argMax(row);
        var permille = (int) (1000.0 * row[choice] / mass);
        return permille >= 500 ? new Forecast(EnemyAction.values()[choice], permille) : Forecast.NONE;
    }

    private void learnOrder2(int key, int action, double forgetting) {
        var slot = find(key);
        if (slot < 0) {
            slot = freeOrEvict();
            if (contextKey[slot] >= 0) {
                evictions++;
            }
            contextKey[slot] = key;
        }
        decayAndAdd(order2[slot], action, forgetting);
    }

    private int freeOrEvict() {
        var lightest = 0;
        var light = Double.MAX_VALUE;
        for (var slot = 0; slot < capacity; slot++) {
            if (contextKey[slot] < 0) {
                return slot;
            }
            var mass = total(order2[slot]);
            if (mass < light) {
                light = mass;
                lightest = slot;
            }
        }
        return lightest;
    }

    private int find(int key) {
        for (var slot = 0; slot < capacity; slot++) {
            if (contextKey[slot] == key) {
                return slot;
            }
        }
        return -1;
    }

    private static void decayAndAdd(float[] row, int action, double forgetting) {
        if (forgetting < 1.0) {
            for (var index = 0; index < row.length; index++) {
                row[index] = (float) (row[index] * forgetting);
            }
        }
        row[action] += 1.0f;
    }

    private static int argMax(float[] row) {
        var best = 0;
        for (var index = 1; index < row.length; index++) {
            if (row[index] > row[best]) {
                best = index;
            }
        }
        return best;
    }

    private static double total(float[] row) {
        var sum = 0.0;
        for (var value : row) {
            sum += value;
        }
        return sum;
    }

    private static int key(int older, int newer) {
        return older | (newer << 4);
    }

    /**
     * A published forecast. A permille of zero means none.
     *
     * @param action the predicted action
     * @param permille probability in thousandths
     */
    public record Forecast(EnemyAction action, int permille) {
        /** No forecast. */
        public static final Forecast NONE = new Forecast(EnemyAction.IDLE, 0);

        /**
         * Whether a caller may use this forecast.
         *
         * @return {@code true} when the permille is positive
         */
        public boolean usable() {
            return permille > 0;
        }
    }
}
