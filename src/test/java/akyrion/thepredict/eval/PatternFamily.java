package akyrion.thepredict.eval;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.domain.PressureLadder;
import akyrion.thepredict.domain.StableRandom;

import java.util.Arrays;

/**
 * Test-family detection for E1 (M26.10). Order-3 scripts, slow bias and skill noise. Thresholds stay at the
 * defaults. The fit family is not used.
 */
public final class PatternFamily {
    private static final double[] LEVELS = {0.6, 0.75, 0.9};
    private static final double[] SKILL = {0.25, 0.5, 0.75, 1.0};
    private static final int ENCOUNTERS = 200;
    private static final int TRIALS = 40;

    private PatternFamily() {
    }

    /**
     * One full read: 24 patterns, three rates, four skills, 200 encounters.
     *
     * @return the measured figures
     */
    public static Figures read() {
        var settings = PatternSettings.DEFAULT;
        var recallHits = new int[LEVELS.length];
        var recallSeen = new int[LEVELS.length];
        var activated = new int[LEVELS.length];
        var entries = new int[LEVELS.length][LEVELS.length * 0 + 24 * SKILL.length * ENCOUNTERS];
        var entryCount = new int[LEVELS.length];
        var adapt = new int[24 * LEVELS.length * SKILL.length * ENCOUNTERS];
        var adaptCount = 0;
        var distinct = 0;
        var lastEntry = -1;
        var encounters = 0;
        var beta = 0;
        var detected = new boolean[80];
        var detectedCount = 0;
        for (var pattern : EnemyPattern.values()) {
            if (!pattern.usesBeta()) {
                continue;
            }
            beta++;
            for (var level = 0; level < LEVELS.length; level++) {
                for (var skill = 0; skill < SKILL.length; skill++) {
                    for (var encounter = 0; encounter < ENCOUNTERS; encounter++) {
                        encounters++;
                        var tracker = new PatternTracker(pattern);
                        var history = 0;
                        var entered = -1;
                        for (var trial = 0; trial < TRIALS; trial++) {
                            var success = bit(pattern.ordinal(), level, skill, encounter, trial, history, LEVELS[level],
                                    SKILL[skill]);
                            var cause = tracker.observe(success, settings.evidenceForgetting(),
                                    settings.confidenceQuantile(), settings.minTrials(), settings.persistUpdates(),
                                    settings.exitMargin(), settings.changepointH());
                            if (entered < 0 && cause.isPresent()) {
                                entered = trial + 1;
                            }
                            history = ((history << 1) | (success ? 1 : 0)) & 7;
                        }
                        if (LEVELS[level] >= 0.75) {
                            recallSeen[level]++;
                            if (entered >= 0) {
                                recallHits[level]++;
                            }
                        }
                        if (entered >= 0) {
                            activated[level]++;
                            entries[level][entryCount[level]++] = entered;
                            if (entered != lastEntry) {
                                distinct++;
                                lastEntry = entered;
                            }
                            adaptCount = adapt(tracker, settings, adapt, adaptCount, pattern.ordinal(), encounter);
                        }
                        if (level == 2 && detectedCount < detected.length) {
                            detected[detectedCount++] = entered >= 0;
                        }
                    }
                }
            }
        }
        var falseHits = falseActivations(settings);
        var predicted = activated[0] + activated[1] + activated[2] + falseHits[0];
        var truePositives = activated[0] + activated[1] + activated[2];
        var halves = new double[80];
        for (var index = 0; index < halves.length; index++) {
            halves[index] = index < detectedCount && detected[index] ? 0.60 : 0.40;
        }
        return new Figures(encounters, beta, ratio(recallHits[2], recallSeen[2]), ratio(recallHits[1], recallSeen[1]),
                ratio(activated[0], 24 * SKILL.length * ENCOUNTERS),
                predicted == 0 ? 0.0 : truePositives / (double) predicted,
                falseHits[1] / (double) ENCOUNTERS, median(entries[2], entryCount[2]),
                median(adapt, adaptCount), distinct, OutcomeOracle.gain(halves),
                DifficultyGlobals.LADDER_K, PressureLadder.bundledMatches());
    }

    private static int adapt(PatternTracker tracker, PatternSettings settings, int[] adapt, int count, int pattern,
                             int encounter) {
        if (tracker.state() != PatternState.ACTIVE && tracker.state() != PatternState.RESTING) {
            return count;
        }
        for (var trial = 0; trial < 40; trial++) {
            var success = unit(pattern, 9, encounter, trial) < 0.5;
            var cause = tracker.observe(success, settings.evidenceForgetting(), settings.confidenceQuantile(),
                    settings.minTrials(), settings.persistUpdates(), settings.exitMargin(), settings.changepointH());
            if (cause.isPresent() && tracker.state() == PatternState.INACTIVE) {
                adapt[count] = trial + 1;
                return count + 1;
            }
        }
        adapt[count] = 40;
        return count + 1;
    }

    private static int[] falseActivations(PatternSettings settings) {
        var hits = 0;
        var worst = 0;
        var ordinal = 0;
        for (var pattern : EnemyPattern.values()) {
            if (!pattern.usesBeta()) {
                continue;
            }
            var patternHits = 0;
            for (var encounter = 0; encounter < ENCOUNTERS; encounter++) {
                var tracker = new PatternTracker(pattern);
                var history = 0;
                for (var trial = 0; trial < 60; trial++) {
                    var success = bit(ordinal, 3, 0, encounter, trial, history, 0.5, 1.0);
                    tracker.observe(success, settings.evidenceForgetting(), settings.confidenceQuantile(),
                            settings.minTrials(), settings.persistUpdates(), settings.exitMargin(),
                            settings.changepointH());
                    history = ((history << 1) | (success ? 1 : 0)) & 7;
                }
                if (tracker.state() == PatternState.ACTIVE || tracker.state() == PatternState.RESTING) {
                    hits++;
                    patternHits++;
                }
            }
            if (patternHits > worst) {
                worst = patternHits;
            }
            ordinal++;
        }
        return new int[] {hits, worst};
    }

    private static boolean bit(int pattern, int level, int skill, int encounter, int trial, int history, double rate,
                               double skillWeight) {
        var slow = 0.02 * StrictMath.sin((encounter + pattern) / 17.0);
        var order = history == 7 ? 0.03 : history == 0 ? -0.02 : 0.0;
        var mean = rate + slow + order;
        if (mean < 0.02) {
            mean = 0.02;
        } else if (mean > 0.98) {
            mean = 0.98;
        }
        var drawn = unit(pattern, level, encounter, trial) < mean;
        var flip = unit(pattern, level + 20, encounter, trial + 1) < (1.0 - skillWeight) * 0.15;
        return flip ? !drawn : drawn;
    }

    private static double unit(int pattern, int level, int encounter, int trial) {
        var word = StableRandom.mix(StableRandom.seed(26L, pattern, encounter) ^ StableRandom.seed(level, trial, 26L));
        return (word >>> 11) / (double) (1L << 53);
    }

    private static double ratio(int hits, int seen) {
        return seen == 0 ? 0.0 : hits / (double) seen;
    }

    private static int median(int[] values, int count) {
        if (count <= 0) {
            return -1;
        }
        var copy = Arrays.copyOf(values, count);
        Arrays.sort(copy);
        return copy[count / 2];
    }

    /**
     * Measured E1 and the ladder fingerprint the same run checked.
     *
     * @param encounters labelled encounters
     * @param patterns beta patterns
     * @param recall90 recall at p = 0.9
     * @param recall75 recall at p = 0.75
     * @param recall60 activation rate at p = 0.6
     * @param precision pooled precision
     * @param falseRate false activations per pattern on {@code none}
     * @param medianDetect median trial of entry at p = 0.9
     * @param medianAdapt median trials until the old pattern leaves
     * @param distinctEntryTrials how many different entry trials showed up
     * @param gsprt decision of the gain contrast
     * @param ladderK frozen k
     * @param ladderMatches whether the bundled bytes match
     */
    public record Figures(int encounters, int patterns, double recall90, double recall75, double recall60,
                          double precision, double falseRate, int medianDetect, int medianAdapt,
                          int distinctEntryTrials, String gsprt, double ladderK, boolean ladderMatches) {
        /**
         * One line for the report.
         *
         * @return the figures
         */
        public String text() {
            return "E1 encounters " + encounters + " patterns " + patterns + " recall90 " + recall90 + " recall75 "
                    + recall75 + " recall60 " + recall60 + " precision " + precision + " false " + falseRate
                    + " detect " + medianDetect + " adapt " + medianAdapt + " distinct " + distinctEntryTrials
                    + " gsprt " + gsprt + " k " + ladderK + " ladder " + ladderMatches;
        }
    }
}
