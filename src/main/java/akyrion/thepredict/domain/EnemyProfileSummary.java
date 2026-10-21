package akyrion.thepredict.domain;

import java.util.Arrays;

/**
 * Versioned profile of one enemy at the close of a fight (M26.9). Counts are integers. The reaction mean is
 * {@code float32}. Order-2 estimates are a Misra–Gries sketch of at most {@link #KEPT} keys and are not part of
 * the associative merge. {@link EnemyPattern#FIXED_CADENCE} has no beta row.
 */
public final class EnemyProfileSummary {
    /** Codec version. An unknown version is rejected. */
    public static final int VERSION = 1;
    /** Action counters. */
    public static final int ACTIONS = EnemyAction.COUNT;
    /** Episode counters. */
    public static final int EPISODES = EnemyEpisode.COUNT;
    /** Beta patterns. Cadence is not one of them. */
    public static final int BETA = 24;
    /** Keys kept by Misra–Gries. */
    public static final int KEPT = 16;
    /** Reaction histogram bins stored in the summary. */
    public static final int BINS = 16;

    private final int version;
    private final int[] actions;
    private final int[] episodes;
    private final int[] success;
    private final int[] failure;
    private final int[] trials;
    private final float reactionMean;
    private final float reactionM2;
    private final int reactionWindows;
    private final int[] transitionKey;
    private final int[] transitionEst;
    private final int transitionTotal;
    private final int patternPeaks;
    private final int[] bins;
    private final int cadenceHits;
    private final int cadenceIqr;

    /**
     * Copies every array.
     *
     * @param version summary version
     * @param actions action counts
     * @param episodes episode counts
     * @param success beta successes, outside the prior
     * @param failure beta failures, outside the prior
     * @param trials beta trials
     * @param reactionMean Chan mean
     * @param reactionM2 Chan second moment
     * @param reactionWindows windows in the reaction mean
     * @param transitionKey kept transition keys
     * @param transitionEst kept estimates
     * @param transitionTotal exact transitions observed
     * @param patternPeaks bits of patterns that were active
     * @param bins histogram counts
     * @param cadenceHits hits of a fixed cadence
     * @param cadenceIqr interquartile range in ticks
     */
    public EnemyProfileSummary(int version, int[] actions, int[] episodes, int[] success, int[] failure, int[] trials,
                               float reactionMean, float reactionM2, int reactionWindows, int[] transitionKey,
                               int[] transitionEst, int transitionTotal, int patternPeaks, int[] bins,
                               int cadenceHits, int cadenceIqr) {
        if (version != VERSION) {
            throw new IllegalArgumentException("unknown summary version " + version);
        }
        this.version = version;
        this.actions = copy(actions, ACTIONS, "actions");
        this.episodes = copy(episodes, EPISODES, "episodes");
        this.success = copy(success, BETA, "success");
        this.failure = copy(failure, BETA, "failure");
        this.trials = copy(trials, BETA, "trials");
        this.reactionMean = reactionMean;
        this.reactionM2 = reactionM2;
        this.reactionWindows = reactionWindows;
        this.transitionKey = copy(transitionKey, transitionKey.length, "transitionKey");
        this.transitionEst = copy(transitionEst, transitionEst.length, "transitionEst");
        if (this.transitionKey.length != this.transitionEst.length || this.transitionKey.length > KEPT) {
            throw new IllegalArgumentException("transition sketch");
        }
        this.transitionTotal = transitionTotal;
        this.patternPeaks = patternPeaks;
        this.bins = copy(bins, BINS, "bins");
        this.cadenceHits = cadenceHits;
        this.cadenceIqr = cadenceIqr;
    }

    /**
     * An empty version-1 summary.
     *
     * @return the summary
     */
    public static EnemyProfileSummary empty() {
        return new EnemyProfileSummary(VERSION, new int[ACTIONS], new int[EPISODES], new int[BETA], new int[BETA],
                new int[BETA], 0f, 0f, 0, new int[0], new int[0], 0, 0, new int[BINS], 0, 0);
    }

    /**
     * The codec version.
     *
     * @return the version
     */
    public int version() {
        return version;
    }

    /**
     * Action counts.
     *
     * @return a copy
     */
    public int[] actions() {
        return actions.clone();
    }

    /**
     * Episode counts.
     *
     * @return a copy
     */
    public int[] episodes() {
        return episodes.clone();
    }

    /**
     * Beta successes.
     *
     * @return a copy
     */
    public int[] success() {
        return success.clone();
    }

    /**
     * Beta failures.
     *
     * @return a copy
     */
    public int[] failure() {
        return failure.clone();
    }

    /**
     * Beta trials.
     *
     * @return a copy
     */
    public int[] trials() {
        return trials.clone();
    }

    /**
     * The reaction mean.
     *
     * @return the mean
     */
    public float reactionMean() {
        return reactionMean;
    }

    /**
     * The reaction second moment.
     *
     * @return the moment
     */
    public float reactionM2() {
        return reactionM2;
    }

    /**
     * Windows inside the reaction mean.
     *
     * @return the window count
     */
    public int reactionWindows() {
        return reactionWindows;
    }

    /**
     * Kept transition keys.
     *
     * @return a copy
     */
    public int[] transitionKey() {
        return transitionKey.clone();
    }

    /**
     * Kept transition estimates.
     *
     * @return a copy
     */
    public int[] transitionEst() {
        return transitionEst.clone();
    }

    /**
     * Exact transitions observed.
     *
     * @return the total
     */
    public int transitionTotal() {
        return transitionTotal;
    }

    /**
     * Patterns that were active.
     *
     * @return the bit mask
     */
    public int patternPeaks() {
        return patternPeaks;
    }

    /**
     * The timing histogram.
     *
     * @return a copy
     */
    public int[] bins() {
        return bins.clone();
    }

    /**
     * Cadence hits.
     *
     * @return the hit count
     */
    public int cadenceHits() {
        return cadenceHits;
    }

    /**
     * Cadence interquartile range.
     *
     * @return the range in ticks
     */
    public int cadenceIqr() {
        return cadenceIqr;
    }

    int[] actionsRaw() {
        return actions;
    }

    int[] episodesRaw() {
        return episodes;
    }

    int[] successRaw() {
        return success;
    }

    int[] failureRaw() {
        return failure;
    }

    int[] trialsRaw() {
        return trials;
    }

    int[] binsRaw() {
        return bins;
    }

    private static int[] copy(int[] source, int length, String name) {
        if (source == null || source.length != length) {
            throw new IllegalArgumentException(name);
        }
        return Arrays.copyOf(source, length);
    }
}
