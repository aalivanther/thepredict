package akyrion.thepredict.domain;

/**
 * Merge of {@link EnemyProfileSummary} (M26.9). Integer counts add exactly. The reaction mean uses Chan's formula.
 * Misra–Gries estimates are merged by summing the sketches and subtracting the (k+1)th count. The estimate is not
 * associative; the exact transition total is.
 */
public final class EnemyProfileMath {
    private EnemyProfileMath() {
    }

    /**
     * Exact merge. The version is the greater one, and both must already be version 1.
     *
     * @param left one summary
     * @param right the other summary
     * @return the merged summary
     */
    public static EnemyProfileSummary merge(EnemyProfileSummary left, EnemyProfileSummary right) {
        var actions = add(left.actionsRaw(), right.actionsRaw());
        var episodes = add(left.episodesRaw(), right.episodesRaw());
        var success = add(left.successRaw(), right.successRaw());
        var failure = add(left.failureRaw(), right.failureRaw());
        var trials = add(left.trialsRaw(), right.trialsRaw());
        var reaction = chan(left.reactionMean(), left.reactionM2(), left.reactionWindows(), right.reactionMean(),
                right.reactionM2(), right.reactionWindows());
        var sketch = mergeSketch(left, right);
        var bins = add(left.binsRaw(), right.binsRaw());
        return new EnemyProfileSummary(EnemyProfileSummary.VERSION, actions, episodes, success, failure, trials,
                reaction[0], reaction[1], (int) reaction[2], sketch.keys, sketch.est, 
                left.transitionTotal() + right.transitionTotal(), left.patternPeaks() | right.patternPeaks(), bins,
                left.cadenceHits() + right.cadenceHits(), Math.max(left.cadenceIqr(), right.cadenceIqr()));
    }

    /**
     * Scales every float mass by a factor in {@code [0, 1]} (M27.2, errata E26-5 applied).
     *
     * @param accumulator the float32 state
     * @param factor scale
     */
    public static void scale(EnemyProfileAccumulator accumulator, float factor) {
        if (factor < 0f || factor > 1f) {
            throw new IllegalArgumentException("factor must be within [0, 1], was " + factor);
        }
        scale(accumulator.actions, factor);
        scale(accumulator.episodes, factor);
        scale(accumulator.success, factor);
        scale(accumulator.failure, factor);
        scale(accumulator.trials, factor);
        accumulator.reactionWindows *= factor;
        accumulator.reactionM2 *= factor;
    }

    /**
     * Weighted add into an accumulator. {@code w = 1} matches {@link #merge} on the integer fields and on the reaction
     * mean and spread: the summary counts as {@code w · n} windows, and Chan's formula adds the cross term
     * {@code δ² · n_a · w·n_b / (n_a + w·n_b)} to the spread.
     *
     * @param accumulator the float32 state
     * @param summary the summary to add
     * @param weight weight in {@code (0, 1]}
     */
    public static void accumulate(EnemyProfileAccumulator accumulator, EnemyProfileSummary summary, float weight) {
        if (!(weight > 0f) || weight > 1f) {
            throw new IllegalArgumentException("weight must be within (0, 1], was " + weight);
        }
        scaleAdd(accumulator.actions, summary.actionsRaw(), weight);
        scaleAdd(accumulator.episodes, summary.episodesRaw(), weight);
        scaleAdd(accumulator.success, summary.successRaw(), weight);
        scaleAdd(accumulator.failure, summary.failureRaw(), weight);
        scaleAdd(accumulator.trials, summary.trialsRaw(), weight);
        var held = accumulator.reactionWindows;
        var added = weight * summary.reactionWindows();
        var windows = held + added;
        if (windows > 0f && summary.reactionWindows() > 0) {
            var delta = summary.reactionMean() - accumulator.reactionMean;
            accumulator.reactionMean += delta * added / windows;
            accumulator.reactionM2 += weight * summary.reactionM2() + delta * delta * held * added / windows;
        }
        accumulator.reactionWindows = windows;
        accumulator.patternPeaks |= summary.patternPeaks();
    }

    /**
     * Estimate of one transition key inside a sketch, or {@code 0} when the key was dropped.
     *
     * @param summary the summary
     * @param key transition key
     * @return the estimate
     */
    public static int estimate(EnemyProfileSummary summary, int key) {
        var keys = summary.transitionKey();
        var est = summary.transitionEst();
        for (var index = 0; index < keys.length; index++) {
            if (keys[index] == key) {
                return est[index];
            }
        }
        return 0;
    }

    /**
     * One online Misra–Gries step. Decrement every counter by one when the sketch would pass {@code k}.
     *
     * @param keys current keys
     * @param est current estimates
     * @param key the observed transition
     * @return the updated sketch, at most {@link EnemyProfileSummary#KEPT} keys
     */
    public static Sketch observe(int[] keys, int[] est, int key) {
        var nextKeys = new int[keys.length + 1];
        var nextEst = new int[est.length + 1];
        var found = false;
        for (var index = 0; index < keys.length; index++) {
            nextKeys[index] = keys[index];
            nextEst[index] = est[index];
            if (keys[index] == key) {
                nextEst[index]++;
                found = true;
            }
        }
        var size = keys.length;
        if (!found) {
            nextKeys[size] = key;
            nextEst[size] = 1;
            size++;
        }
        if (size <= EnemyProfileSummary.KEPT) {
            return new Sketch(copy(nextKeys, size), copy(nextEst, size));
        }
        return decremented(nextKeys, nextEst, size);
    }

    /** Takes one from every counter and drops the keys that reach zero, keeping their order. */
    private static Sketch decremented(int[] keys, int[] est, int size) {
        var kept = 0;
        for (var index = 0; index < size; index++) {
            if (est[index] > 1) {
                keys[kept] = keys[index];
                est[kept] = est[index] - 1;
                kept++;
            }
        }
        return new Sketch(copy(keys, kept), copy(est, kept));
    }

    private static Sketch mergeSketch(EnemyProfileSummary left, EnemyProfileSummary right) {
        var keys = new int[EnemyProfileSummary.KEPT * 2];
        var est = new int[keys.length];
        var size = put(keys, est, 0, left.transitionKey(), left.transitionEst());
        size = put(keys, est, size, right.transitionKey(), right.transitionEst());
        if (size <= EnemyProfileSummary.KEPT) {
            return new Sketch(copy(keys, size), copy(est, size));
        }
        var order = order(est, size);
        var cut = est[order[EnemyProfileSummary.KEPT]];
        var kept = 0;
        for (var index = 0; index < size; index++) {
            var value = est[index] - cut;
            if (value > 0) {
                keys[kept] = keys[index];
                est[kept] = value;
                kept++;
            }
        }
        return new Sketch(copy(keys, kept), copy(est, kept));
    }

    private static int put(int[] keys, int[] est, int size, int[] moreKeys, int[] moreEst) {
        for (var index = 0; index < moreKeys.length; index++) {
            var found = -1;
            for (var slot = 0; slot < size; slot++) {
                if (keys[slot] == moreKeys[index]) {
                    found = slot;
                    break;
                }
            }
            if (found >= 0) {
                est[found] += moreEst[index];
            } else {
                keys[size] = moreKeys[index];
                est[size] = moreEst[index];
                size++;
            }
        }
        return size;
    }

    private static int[] order(int[] est, int size) {
        var order = new int[size];
        for (var index = 0; index < size; index++) {
            order[index] = index;
        }
        for (var left = 1; left < size; left++) {
            var value = order[left];
            var right = left - 1;
            while (right >= 0 && est[order[right]] < est[value]) {
                order[right + 1] = order[right];
                right--;
            }
            order[right + 1] = value;
        }
        return order;
    }

    private static float[] chan(float leftMean, float leftM2, int leftN, float rightMean, float rightM2, int rightN) {
        if (leftN == 0) {
            return new float[] {rightMean, rightM2, rightN};
        }
        if (rightN == 0) {
            return new float[] {leftMean, leftM2, leftN};
        }
        var n = leftN + rightN;
        var delta = rightMean - leftMean;
        var mean = leftMean + delta * rightN / n;
        var m2 = leftM2 + rightM2 + delta * delta * leftN * rightN / n;
        return new float[] {mean, m2, n};
    }

    private static int[] add(int[] left, int[] right) {
        var sum = new int[left.length];
        for (var index = 0; index < left.length; index++) {
            sum[index] = left[index] + right[index];
        }
        return sum;
    }

    private static void scale(float[] values, float factor) {
        for (var index = 0; index < values.length; index++) {
            values[index] *= factor;
        }
    }

    private static void scaleAdd(float[] into, int[] values, float weight) {
        for (var index = 0; index < into.length; index++) {
            into[index] += weight * values[index];
        }
    }

    private static int[] copy(int[] source, int length) {
        var copy = new int[length];
        System.arraycopy(source, 0, copy, 0, length);
        return copy;
    }

    /**
     * A Misra–Gries sketch.
     *
     * @param keys transition keys
     * @param est estimates
     */
    public record Sketch(int[] keys, int[] est) {
    }
}
