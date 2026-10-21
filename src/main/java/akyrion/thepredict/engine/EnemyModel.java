package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionPredictor;
import akyrion.thepredict.domain.BetaEvidence;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyProfileSummary;
import akyrion.thepredict.domain.GammaRate;
import akyrion.thepredict.domain.PatternCause;
import akyrion.thepredict.domain.PatternRules;
import akyrion.thepredict.domain.PatternSeed;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.domain.StimulusWindow;
import akyrion.thepredict.domain.StyleMemory;
import akyrion.thepredict.domain.TimingHistogram;

import java.util.UUID;

/**
 * Per-enemy state of one encounter (M26.3). It is created when the enemy is admitted and dropped after the summary.
 * The kinematic word is stored and not decoded here.
 */
public final class EnemyModel {
    private final UUID id;
    private final int order2Contexts;
    private final ActionPredictor predictor;
    private final PatternTracker[] trackers = new PatternTracker[EnemyPattern.values().length - 1];
    private final TimingHistogram[] histograms = new TimingHistogram[4];
    private final GammaRate rates = new GammaRate();
    private final int[] actions = new int[EnemyAction.COUNT];
    private final float[] ownedSuccess = new float[StyleMemory.PATTERNS];
    private final float[] ownedFailure = new float[StyleMemory.PATTERNS];
    private final int[] activations = new int[StyleMemory.PATTERNS];
    private final long[] restEnds = new long[StyleMemory.PATTERNS];
    private final int minOwnTrials;
    private final boolean seeded;
    private final StimulusWindow windows = new StimulusWindow();
    private final int[] recent = new int[8];
    private long kinematics;
    private int mask;
    private int forecast = -1;
    private int recentCount;
    private int recentAt;
    private double forecastFloor = PatternSettings.DEFAULT.forecastMinAccuracy();
    private int changeOrdinal = -1;
    private int changeCause = -1;
    private int changeState = -1;
    private int activated;

    /**
     * An empty model with no seed.
     *
     * @param id the enemy
     * @param order2Contexts order-2 rows
     * @param binTicks histogram bin width
     */
    public EnemyModel(UUID id, int order2Contexts, int binTicks) {
        this(id, order2Contexts, binTicks, PatternSeed.NONE, 0);
    }

    /**
     * A model seeded from a player's style. The action predictor is not seeded.
     *
     * @param id the enemy
     * @param order2Contexts order-2 rows
     * @param binTicks histogram bin width
     * @param seed pattern seed, {@link PatternSeed#NONE} when there is none
     * @param minOwnTrials own trials required before a seeded pattern may activate
     */
    public EnemyModel(UUID id, int order2Contexts, int binTicks, PatternSeed seed, int minOwnTrials) {
        this.id = id;
        this.order2Contexts = order2Contexts;
        this.predictor = new ActionPredictor(order2Contexts);
        this.minOwnTrials = minOwnTrials;
        this.seeded = seed.trials() > 0.0;
        var ordinal = 0;
        for (var pattern : EnemyPattern.values()) {
            if (pattern.usesBeta()) {
                var tracker = new PatternTracker(pattern);
                if (seeded) {
                    tracker.evidence().seed(seed.successes()[pattern.ordinal()], seed.failures()[pattern.ordinal()]);
                }
                trackers[ordinal++] = tracker;
            }
        }
        for (var index = 0; index < histograms.length; index++) {
            histograms[index] = new TimingHistogram(binTicks, 16);
        }
    }

    /**
     * The enemy.
     *
     * @return the id
     */
    public UUID id() {
        return id;
    }

    /**
     * The stored kinematic word.
     *
     * @return the word, {@code 0} when none
     */
    public long kinematics() {
        return kinematics;
    }

    /**
     * Replaces the stored word. The stamp stays opaque.
     *
     * @param word the word
     */
    public void kinematics(long word) {
        this.kinematics = word;
    }

    /**
     * Learns one token and refreshes the mask. A pattern is observed only on the occasions of its family.
     *
     * @param action the token
     * @param settings detector limits
     */
    public void learn(EnemyAction action, PatternSettings settings) {
        learn(action, settings, false);
    }

    /**
     * Learns one token. Every pattern whose family holds the token ({@link EnemyOccasions}) observes one trial: a
     * success when the token is its own occasion, a failure otherwise. A spear is no trial of {@code SPAMS_CLICKS}
     * or {@code CRIT_JUMPER} (M26 B2).
     *
     * @param action the token
     * @param settings detector limits
     * @param spear whether the weapon was a spear
     */
    public void learn(EnemyAction action, PatternSettings settings, boolean spear) {
        var predicted = predictor.predict();
        predictor.update(action.ordinal(), predicted, settings.forgetting());
        if (action.ordinal() < actions.length) {
            actions[action.ordinal()]++;
        }
        remember(action.ordinal());
        for (var tracker : trackers) {
            if (tracker != null && trial(tracker.pattern(), action, spear)) {
                observe(tracker, counts(tracker.pattern(), action, spear), settings);
            }
        }
        forecastFloor = settings.forecastMinAccuracy();
        refresh(forecastFloor);
    }

    private static boolean trial(EnemyPattern pattern, EnemyAction action, boolean spear) {
        if (spear && (pattern == EnemyPattern.SPAMS_CLICKS || pattern == EnemyPattern.CRIT_JUMPER)) {
            return false;
        }
        return EnemyOccasions.trial(pattern, action);
    }

    private static boolean counts(EnemyPattern pattern, EnemyAction action, boolean spear) {
        if (pattern == EnemyPattern.SPAMS_CLICKS || pattern == EnemyPattern.CRIT_JUMPER) {
            return PatternRules.rhythmSuccess(pattern, action, spear);
        }
        return PatternRules.matches(pattern, action);
    }

    private void observe(PatternTracker tracker, boolean success, PatternSettings settings) {
        var ordinal = tracker.pattern().ordinal();
        if (success) {
            ownedSuccess[ordinal]++;
        } else {
            ownedFailure[ordinal]++;
        }
        var cause = seeded
                ? tracker.observe(success, settings.evidenceForgetting(), settings.confidenceQuantile(),
                settings.minTrials(), settings.persistUpdates(), settings.exitMargin(), settings.changepointH(),
                minOwnTrials)
                : tracker.observe(success, settings.evidenceForgetting(), settings.confidenceQuantile(),
                settings.minTrials(), settings.persistUpdates(), settings.exitMargin(), settings.changepointH());
        cause.ifPresent(found -> noteChange(tracker, found));
    }

    /**
     * Opens a rest and drops the pattern from the active mask until {@code untilTick}. When two members of the
     * encounter rest the same pattern, the later end holds.
     *
     * @param pattern the pattern
     * @param untilTick first tick at which {@link #endRests} may close the rest
     */
    public void rest(EnemyPattern pattern, long untilTick) {
        var tracker = tracker(pattern);
        if (tracker == null) {
            return;
        }
        tracker.rest();
        var ordinal = pattern.ordinal();
        if (ordinal < restEnds.length) {
            restEnds[ordinal] = Math.max(restEnds[ordinal], untilTick);
        }
        noteChange(tracker, PatternCause.REST);
        refresh(forecastFloor);
    }

    /**
     * Closes every rest whose end has come (PD-02). A pattern whose mean is still above its theta goes back to
     * {@code ACTIVE}, which counts and signals like an activation; otherwise it leaves to {@code INACTIVE}.
     *
     * @param tick server tick
     * @return whether a rest closed
     */
    public boolean endRests(long tick) {
        var closed = false;
        for (var tracker : trackers) {
            if (tracker == null || tracker.state() != PatternState.RESTING) {
                continue;
            }
            var ordinal = tracker.pattern().ordinal();
            if (ordinal >= restEnds.length || restEnds[ordinal] > tick) {
                continue;
            }
            restEnds[ordinal] = 0L;
            noteChange(tracker, tracker.endRest());
            closed = true;
        }
        if (closed) {
            refresh(forecastFloor);
        }
        return closed;
    }

    /**
     * Occasion windows of this enemy.
     *
     * @return the ring
     */
    public StimulusWindow windows() {
        return windows;
    }

    /**
     * The tracker of one pattern. Cadence has none.
     *
     * @param pattern the pattern
     * @return the tracker, or {@code null}
     */
    public PatternTracker tracker(EnemyPattern pattern) {
        if (!pattern.usesBeta()) {
            return null;
        }
        for (var tracker : trackers) {
            if (tracker != null && tracker.pattern() == pattern) {
                return tracker;
            }
        }
        return null;
    }

    /**
     * Running accuracy of the action predictor.
     *
     * @return the fraction in {@code [0, 1]}
     */
    public double accuracy() {
        return predictor.accuracy();
    }

    /**
     * Takes the latest state change, if one is waiting.
     *
     * @return the pattern ordinal, or {@code -1}
     */
    public int pollChange() {
        var ordinal = changeOrdinal;
        changeOrdinal = -1;
        return ordinal;
    }

    /**
     * Cause ordinal of the change just polled.
     *
     * @return the {@link PatternCause} ordinal
     */
    public int changeCause() {
        return changeCause;
    }

    /**
     * State ordinal of the change just polled.
     *
     * @return the {@link PatternState} ordinal
     */
    public int changeState() {
        return changeState;
    }

    /**
     * Recent token names, or {@code none}.
     *
     * @return the text
     */
    public String tokens() {
        if (recentCount == 0) {
            return "none";
        }
        var text = new StringBuilder();
        var start = recentCount < recent.length ? 0 : recentAt;
        for (var index = 0; index < recentCount; index++) {
            if (index > 0) {
                text.append(' ');
            }
            text.append(EnemyAction.values()[recent[(start + index) % recent.length]].name()
                    .toLowerCase(java.util.Locale.ROOT));
        }
        return text.toString();
    }

    /**
     * A summary built from the counts of this fight. Allocated only when an encounter closes.
     *
     * @return the summary
     */
    public EnemyProfileSummary summary() {
        var success = new int[EnemyProfileSummary.BETA];
        var failure = new int[EnemyProfileSummary.BETA];
        var trials = new int[EnemyProfileSummary.BETA];
        for (var tracker : trackers) {
            if (tracker == null || tracker.pattern().ordinal() >= success.length) {
                continue;
            }
            var ordinal = tracker.pattern().ordinal();
            success[ordinal] = (int) Math.round(tracker.evidence().alpha() - BetaEvidence.PRIOR);
            failure[ordinal] = (int) Math.round(tracker.evidence().beta() - BetaEvidence.PRIOR);
            trials[ordinal] = tracker.evidence().trials();
        }
        return new EnemyProfileSummary(EnemyProfileSummary.VERSION, actions.clone(),
                new int[EnemyProfileSummary.EPISODES], success, failure, trials, 0f, 0f, 0, new int[0], new int[0],
                0, mask, new int[EnemyProfileSummary.BINS], 0, 0);
    }

    private void refresh(double forecastMinAccuracy) {
        mask = 0;
        for (var tracker : trackers) {
            if (tracker != null && tracker.state() == PatternState.ACTIVE) {
                mask |= tracker.pattern().bit();
            }
        }
        var published = predictor.forecast(forecastMinAccuracy);
        forecast = published.permille() == 0 ? -1 : published.action().ordinal();
    }

    private void remember(int action) {
        recent[recentAt] = action;
        recentAt = (recentAt + 1) % recent.length;
        if (recentCount < recent.length) {
            recentCount++;
        }
    }

    private void noteChange(PatternTracker tracker, PatternCause cause) {
        changeOrdinal = tracker.pattern().ordinal();
        changeCause = cause.ordinal();
        changeState = tracker.state().ordinal();
        if (!akyrion.thepredict.domain.PatternSignals.activation(cause, tracker.state())) {
            return;
        }
        if (changeOrdinal < activations.length) {
            activations[changeOrdinal]++;
        }
        activated |= tracker.pattern().bit();
    }

    /**
     * Takes the patterns that became active since the last call, every one of them even when one token activated
     * several, so each activation is signalled once (M26.11, DOC-01).
     *
     * @return the bits of {@link EnemyPattern#bit()}, {@code 0} when none
     */
    public int pollActivated() {
        var bits = activated;
        activated = 0;
        return bits;
    }

    /**
     * Transitions of one pattern into {@code ACTIVE} in this encounter, for {@code m26_pattern_stats} (M26.9).
     *
     * @param pattern the pattern
     * @return the count, {@code 0} for a pattern without a tracker
     */
    public int activations(EnemyPattern pattern) {
        return pattern.ordinal() < activations.length ? activations[pattern.ordinal()] : 0;
    }

    /**
     * Trials of one pattern observed in this encounter, without the seed of memory (M26.9: the encounters with
     * trials).
     *
     * @param pattern the pattern
     * @return the count
     */
    public int ownTrials(EnemyPattern pattern) {
        var ordinal = pattern.ordinal();
        return ordinal < ownedSuccess.length ? (int) (ownedSuccess[ordinal] + ownedFailure[ordinal]) : 0;
    }

    /**
     * Active pattern bits.
     *
     * @return the mask, zero when none is active
     */
    public int activeMask() {
        return mask;
    }

    /**
     * Forecast ordinal.
     *
     * @return the action, or {@code -1} when the forecast is not used
     */
    public int forecast() {
        return forecast;
    }

    /**
     * Raw fight counts, without the seed. The action predictor is not included.
     *
     * @return the style of this encounter
     */
    public StyleMemory fightStyle() {
        var cusum = new int[StyleMemory.PATTERNS];
        var masses = new float[StyleMemory.ACTIONS];
        var keys = new int[0];
        var weights = new float[0];
        var histogramMass = new float[StyleMemory.HISTOGRAMS];
        var shapes = new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS];
        var episodes = new float[StyleMemory.EPISODES];
        return new StyleMemory(1f, 0, ownedSuccess.clone(), ownedFailure.clone(), cusum, masses, 0f, keys, weights,
                histogramMass, shapes, 0f, 0f, 0f, episodes, episodes.clone());
    }

    /**
     * Bytes of the arrays this model owns. Object headers are not included. The X3 figure of 3860 is this class of
     * number; the live object is larger.
     *
     * @return array bytes
     */
    public int arrayBytes() {
        var bytes = EnemyAction.COUNT * EnemyAction.COUNT * 4;
        bytes += order2Contexts * EnemyAction.COUNT * 4 + order2Contexts * 4;
        bytes += trackers.length * (8 + 8 + 4);
        bytes += histograms.length * 16 * 4;
        bytes += GammaRate.SERIES * 8;
        bytes += actions.length * 4;
        bytes += ownedSuccess.length * 4 + ownedFailure.length * 4;
        bytes += activations.length * 4;
        return bytes;
    }

    /**
     * A conservative live size: array bytes plus a 48-byte header for the model, the predictor, each tracker, each
     * histogram and the rate table. This is not JOL. JOL is not on the compile classpath (parada 13.5).
     *
     * @return the estimate
     */
    public int estimatedBytes() {
        return arrayBytes() + 48 * (3 + trackers.length + histograms.length);
    }
}
