package akyrion.thepredict.domain;

import java.util.Arrays;

/**
 * Reduces a transition sketch to eight keys (M27.2). The ninth largest weight is subtracted and non-positive keys
 * are dropped. The estimate may sit at most one float16 ULP above the true weight.
 */
public final class TransitionReducer {
    /** Keys kept. */
    public static final int KEPT = 8;

    private TransitionReducer() {
    }

    /**
     * Scales, merges and reduces.
     *
     * @param keys stored keys
     * @param weights stored weights
     * @param factor scale in {@code [0, 1]}
     * @param addedKeys keys of this fight
     * @param addedWeights weights of this fight
     * @return at most eight keys
     */
    public static Sketch merge(int[] keys, float[] weights, float factor, int[] addedKeys, float[] addedWeights) {
        var mergedKeys = new int[keys.length + addedKeys.length];
        var merged = new float[mergedKeys.length];
        var size = scaleInto(keys, weights, factor, mergedKeys, merged);
        for (var index = 0; index < addedKeys.length; index++) {
            size = add(mergedKeys, merged, size, addedKeys[index], addedWeights[index]);
        }
        return reduce(mergedKeys, merged, size);
    }

    private static int scaleInto(int[] keys, float[] weights, float factor, int[] intoKeys, float[] into) {
        var size = 0;
        for (var index = 0; index < keys.length; index++) {
            var weight = weights[index] * factor;
            if (weight > 0f) {
                intoKeys[size] = keys[index];
                into[size] = weight;
                size++;
            }
        }
        return size;
    }

    private static int add(int[] keys, float[] weights, int size, int key, float weight) {
        for (var index = 0; index < size; index++) {
            if (keys[index] == key) {
                weights[index] += weight;
                return size;
            }
        }
        keys[size] = key;
        weights[size] = weight;
        return size + 1;
    }

    private static Sketch reduce(int[] keys, float[] weights, int size) {
        if (size <= KEPT) {
            return copy(keys, weights, size);
        }
        var order = sorted(weights, size);
        var ninth = weights[order[KEPT]];
        var keptKeys = new int[size];
        var kept = new float[size];
        var count = 0;
        for (var index = 0; index < size; index++) {
            var weight = weights[index] - ninth;
            if (weight > 0f) {
                keptKeys[count] = keys[index];
                kept[count] = weight;
                count++;
            }
        }
        return copy(keptKeys, kept, Math.min(count, KEPT));
    }

    private static int[] sorted(float[] weights, int size) {
        var order = new int[size];
        for (var index = 0; index < size; index++) {
            order[index] = index;
        }
        for (var left = 0; left < size; left++) {
            for (var right = left + 1; right < size; right++) {
                if (weights[order[right]] > weights[order[left]]) {
                    var held = order[left];
                    order[left] = order[right];
                    order[right] = held;
                }
            }
        }
        return order;
    }

    private static Sketch copy(int[] keys, float[] weights, int size) {
        return new Sketch(Arrays.copyOf(keys, size), Arrays.copyOf(weights, size));
    }

    /**
     * A reduced sketch.
     *
     * @param keys keys
     * @param weights weights
     */
    public record Sketch(int[] keys, float[] weights) {
        /**
         * Copies the arrays.
         */
        public Sketch {
            keys = keys.clone();
            weights = weights.clone();
        }
    }
}
