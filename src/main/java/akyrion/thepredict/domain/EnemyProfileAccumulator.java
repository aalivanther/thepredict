package akyrion.thepredict.domain;

/**
 * Float32 accumulator M27 will weight across fights (M26.9). M26 only defines it. It is not a summary and it is
 * not serialized. {@code w = 1} matches an integer merge while every sum stays inside the exact integer range of
 * {@code float32}.
 */
public final class EnemyProfileAccumulator {
    final float[] actions = new float[EnemyProfileSummary.ACTIONS];
    final float[] episodes = new float[EnemyProfileSummary.EPISODES];
    final float[] success = new float[EnemyProfileSummary.BETA];
    final float[] failure = new float[EnemyProfileSummary.BETA];
    final float[] trials = new float[EnemyProfileSummary.BETA];
    float reactionMean;
    float reactionM2;
    float reactionWindows;
    int patternPeaks;

    /** An empty accumulator. */
    public EnemyProfileAccumulator() {
    }

    /**
     * Action count at an index.
     *
     * @param index action ordinal
     * @return the accumulated count
     */
    public float action(int index) {
        return actions[index];
    }

    /**
     * Episode count at an index.
     *
     * @param index episode ordinal
     * @return the accumulated count
     */
    public float episode(int index) {
        return episodes[index];
    }

    /**
     * Beta successes at a pattern ordinal below 24.
     *
     * @param index pattern ordinal
     * @return the accumulated successes
     */
    public float success(int index) {
        return success[index];
    }

    /**
     * The running reaction mean.
     *
     * @return the mean
     */
    public float reactionMean() {
        return reactionMean;
    }

    /**
     * Patterns that were active at least once.
     *
     * @return the bit mask
     */
    public int patternPeaks() {
        return patternPeaks;
    }
}
