package akyrion.thepredict.domain;

import java.util.Arrays;

/**
 * Forgotten style of one player (M27.1). Pattern slots follow the codec vocabulary of 25, not the 24 Beta rows.
 * Arrays are copied.
 */
public final class StyleMemory {
    /** Action vocabulary of version 1. */
    public static final int ACTIONS = 16;
    /** Pattern vocabulary of version 1. */
    public static final int PATTERNS = 25;
    /** Histograms stored with the style. */
    public static final int HISTOGRAMS = 4;
    /** Bins of one histogram. */
    public static final int BINS = 16;
    /** Episodes stored with the style. */
    public static final int EPISODES = 10;
    /** Transitions kept after the reduction to k = 8. */
    public static final int TOP = 8;

    private final float weight;
    private final int mask;
    private final float[] success;
    private final float[] failure;
    private final int[] cusum;
    private final float[] actions;
    private final float transitionTotal;
    private final int[] transitionKeys;
    private final float[] transitionWeights;
    private final float[] histogramMass;
    private final byte[][] histogramShape;
    private final float reactionWeight;
    private final float reactionMean;
    private final float reactionM2;
    private final float[] episodeCounts;
    private final float[] episodeSeconds;

    /**
     * Copies every array. The mask must match the pattern slots that are written.
     *
     * @param weight fight weight of the style
     * @param mask bits of patterns that are present
     * @param success successes per pattern
     * @param failure failures per pattern
     * @param cusum CUSUM step in tenths, one byte per pattern
     * @param actions action masses
     * @param transitionTotal exact transition weight
     * @param transitionKeys keys of the kept transitions
     * @param transitionWeights weights of the kept transitions
     * @param histogramMass mass of each histogram
     * @param histogramShape 16 cells of each histogram
     * @param reactionWeight windows of the reaction
     * @param reactionMean reaction mean
     * @param reactionM2 reaction second moment
     * @param episodeCounts episode counts
     * @param episodeSeconds episode seconds
     */
    public StyleMemory(float weight, int mask, float[] success, float[] failure, int[] cusum, float[] actions,
                       float transitionTotal, int[] transitionKeys, float[] transitionWeights, float[] histogramMass,
                       byte[][] histogramShape, float reactionWeight, float reactionMean, float reactionM2,
                       float[] episodeCounts, float[] episodeSeconds) {
        this.weight = weight;
        this.mask = mask;
        this.success = copy(success, PATTERNS);
        this.failure = copy(failure, PATTERNS);
        this.cusum = copy(cusum, PATTERNS);
        this.actions = copy(actions, ACTIONS);
        this.transitionTotal = transitionTotal;
        this.transitionKeys = Arrays.copyOf(transitionKeys, transitionKeys.length);
        this.transitionWeights = Arrays.copyOf(transitionWeights, transitionWeights.length);
        this.histogramMass = copy(histogramMass, HISTOGRAMS);
        this.histogramShape = copyShapes(histogramShape);
        this.reactionWeight = reactionWeight;
        this.reactionMean = reactionMean;
        this.reactionM2 = reactionM2;
        this.episodeCounts = copy(episodeCounts, EPISODES);
        this.episodeSeconds = copy(episodeSeconds, EPISODES);
    }

    /**
     * Fight weight.
     *
     * @return the weight
     */
    public float weight() {
        return weight;
    }

    /**
     * Bits of patterns that are stored.
     *
     * @return the mask
     */
    public int mask() {
        return mask;
    }

    /**
     * Successes of one pattern.
     *
     * @param pattern ordinal
     * @return the mass
     */
    public float success(int pattern) {
        return success[pattern];
    }

    /**
     * Failures of one pattern.
     *
     * @param pattern ordinal
     * @return the mass
     */
    public float failure(int pattern) {
        return failure[pattern];
    }

    /**
     * Stored CUSUM byte of one pattern.
     *
     * @param pattern ordinal
     * @return the byte, step 0.1
     */
    public int cusum(int pattern) {
        return cusum[pattern] & 0xFF;
    }

    /**
     * A copy of the action masses.
     *
     * @return 16 masses
     */
    public float[] actions() {
        return actions.clone();
    }

    /**
     * Exact transition weight.
     *
     * @return the total
     */
    public float transitionTotal() {
        return transitionTotal;
    }

    /**
     * Keys of the kept transitions.
     *
     * @return a copy
     */
    public int[] transitionKeys() {
        return transitionKeys.clone();
    }

    /**
     * Weights of the kept transitions.
     *
     * @return a copy
     */
    public float[] transitionWeights() {
        return transitionWeights.clone();
    }

    /**
     * Mass of one histogram.
     *
     * @param index histogram index
     * @return the mass
     */
    public float histogramMass(int index) {
        return histogramMass[index];
    }

    /**
     * Cells of one histogram.
     *
     * @param index histogram index
     * @return 16 cells
     */
    public byte[] histogramShape(int index) {
        return histogramShape[index].clone();
    }

    /**
     * Reaction window weight.
     *
     * @return the weight
     */
    public float reactionWeight() {
        return reactionWeight;
    }

    /**
     * Reaction mean.
     *
     * @return the mean
     */
    public float reactionMean() {
        return reactionMean;
    }

    /**
     * Reaction second moment.
     *
     * @return the moment
     */
    public float reactionM2() {
        return reactionM2;
    }

    /**
     * Episode counts.
     *
     * @return a copy of 10 counts
     */
    public float[] episodeCounts() {
        return episodeCounts.clone();
    }

    /**
     * Episode seconds.
     *
     * @return a copy of 10 durations
     */
    public float[] episodeSeconds() {
        return episodeSeconds.clone();
    }

    private static float[] copy(float[] values, int length) {
        if (values.length != length) {
            throw new IllegalArgumentException("length " + values.length + " expected " + length);
        }
        return values.clone();
    }

    private static int[] copy(int[] values, int length) {
        if (values.length != length) {
            throw new IllegalArgumentException("length " + values.length + " expected " + length);
        }
        return values.clone();
    }

    private static byte[][] copyShapes(byte[][] shapes) {
        if (shapes.length != HISTOGRAMS) {
            throw new IllegalArgumentException("histograms " + shapes.length);
        }
        var copy = new byte[HISTOGRAMS][];
        for (var index = 0; index < HISTOGRAMS; index++) {
            if (shapes[index].length != BINS) {
                throw new IllegalArgumentException("histogram bins");
            }
            copy[index] = shapes[index].clone();
        }
        return copy;
    }
}
