package akyrion.thepredict.domain;

import java.util.Optional;

/**
 * Forgets a style and folds one fight into it (M27.2). Masses are scaled by {@code γ·τ} before the fight is added.
 * Transitions merge equal keys and are reduced to eight keys by {@link TransitionReducer}; their total is the exact
 * {@code γ·τ·stored + fight} sum. The reaction weight and its second moment are scaled together. Histogram cells are
 * requantized to 255.
 */
public final class StyleFold {
    private StyleFold() {
    }

    /**
     * The folded style and how many fields saturated.
     *
     * @param style the style
     * @param saturated fields clamped to the float16 range
     * @param breaks pattern breaks applied
     */
    public record Result(Optional<StyleMemory> style, int saturated, int breaks) {
    }

    /**
     * Folds one fight. An absent fight leaves the decayed style.
     *
     * @param stored the stored style
     * @param fight the fight style
     * @param gamma {@code γ·τ}
     * @param threshold CUSUM threshold
     * @param rho break factor
     * @return the fold
     */
    public static Result apply(Optional<StyleMemory> stored, Optional<StyleMemory> fight, double gamma,
                               double threshold, double rho) {
        if (stored.isEmpty() && fight.isEmpty()) {
            return new Result(Optional.empty(), 0, 0);
        }
        var success = new float[StyleMemory.PATTERNS];
        var failure = new float[StyleMemory.PATTERNS];
        var cusum = new int[StyleMemory.PATTERNS];
        var actions = new float[StyleMemory.ACTIONS];
        var saturated = new int[] {0};
        var breaks = copyScaled(stored, gamma, success, failure, cusum, actions, saturated);
        breaks += addFight(fight, success, failure, cusum, actions, threshold, rho, saturated);
        var transitions = transitions(stored, fight, gamma, saturated);
        var histograms = histograms(stored, fight, gamma, saturated);
        var reaction = reaction(stored, fight, gamma);
        var episodes = episodes(stored, fight, gamma, saturated);
        var weight = (float) (gamma * stored.map(StyleMemory::weight).orElse(0f)
                + fight.map(StyleMemory::weight).orElse(0f));
        var mask = mask(success, failure);
        var style = new StyleMemory(PlayerMemoryMath.saturate(weight), mask, success, failure, cusum, actions,
                transitions.total(), transitions.keys(), transitions.weights(), histograms.mass(), histograms.shapes(),
                reaction[0], reaction[1], reaction[2], episodes.counts(), episodes.seconds());
        if (PlayerMemoryMath.saturated(weight)) {
            saturated[0]++;
        }
        return new Result(Optional.of(style), saturated[0], breaks);
    }

    private static int copyScaled(Optional<StyleMemory> stored, double gamma, float[] success, float[] failure,
                                  int[] cusum, float[] actions, int[] saturated) {
        if (stored.isEmpty()) {
            return 0;
        }
        var style = stored.get();
        for (var pattern = 0; pattern < StyleMemory.PATTERNS; pattern++) {
            success[pattern] = scale(style.success(pattern), gamma, saturated);
            failure[pattern] = scale(style.failure(pattern), gamma, saturated);
            cusum[pattern] = style.cusum(pattern);
        }
        var masses = style.actions();
        for (var index = 0; index < masses.length; index++) {
            actions[index] = scale(masses[index], gamma, saturated);
        }
        return 0;
    }

    private static int addFight(Optional<StyleMemory> fight, float[] success, float[] failure, int[] cusum,
                                float[] actions, double threshold, double rho, int[] saturated) {
        if (fight.isEmpty()) {
            return 0;
        }
        var style = fight.get();
        var breaks = 0;
        for (var pattern = 0; pattern < StyleMemory.PATTERNS; pattern++) {
            var fightS = Math.round(style.success(pattern));
            var fightF = Math.round(style.failure(pattern));
            if (fightS == 0 && fightF == 0) {
                continue;
            }
            var updated = PlayerMemoryMath.update(success[pattern], failure[pattern],
                    PlayerMemoryMath.cusumValue(cusum[pattern]), fightS, fightF, 1.0, threshold, rho,
                    PlayerMemoryMath.HABIT_THETA);
            success[pattern] = scale(updated.successes(), 1.0, saturated);
            failure[pattern] = scale(updated.failures(), 1.0, saturated);
            cusum[pattern] = PlayerMemoryMath.cusumByte(updated.cusum());
            breaks += updated.breaks();
        }
        var masses = style.actions();
        for (var index = 0; index < masses.length; index++) {
            actions[index] = scale(actions[index] + masses[index], 1.0, saturated);
        }
        return breaks;
    }

    private static float scale(double value, double gamma, int[] saturated) {
        var scaled = (float) (value * gamma);
        if (PlayerMemoryMath.saturated(scaled)) {
            saturated[0]++;
        }
        return PlayerMemoryMath.saturate(scaled);
    }

    private static int mask(float[] success, float[] failure) {
        var bits = 0;
        for (var pattern = 0; pattern < success.length; pattern++) {
            if (success[pattern] + failure[pattern] > 0f) {
                bits |= 1 << pattern;
            }
        }
        return bits;
    }

    private static Transition transitions(Optional<StyleMemory> stored, Optional<StyleMemory> fight, double gamma,
                                          int[] saturated) {
        var sketch = TransitionReducer.merge(stored.map(StyleMemory::transitionKeys).orElse(new int[0]),
                stored.map(StyleMemory::transitionWeights).orElse(new float[0]), (float) gamma,
                fight.map(StyleMemory::transitionKeys).orElse(new int[0]),
                fight.map(StyleMemory::transitionWeights).orElse(new float[0]));
        var weights = sketch.weights().clone();
        for (var index = 0; index < weights.length; index++) {
            weights[index] = scale(weights[index], 1.0, saturated);
        }
        var total = gamma * stored.map(StyleMemory::transitionTotal).orElse(0f)
                + fight.map(StyleMemory::transitionTotal).orElse(0f);
        return new Transition(sketch.keys(), weights, PlayerMemoryMath.saturate((float) total));
    }

    private static Histogram histograms(Optional<StyleMemory> stored, Optional<StyleMemory> fight, double gamma,
                                        int[] saturated) {
        var mass = new float[StyleMemory.HISTOGRAMS];
        var shape = new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS];
        var cells = new double[StyleMemory.HISTOGRAMS][StyleMemory.BINS];
        addHistogram(stored, gamma, cells, saturated);
        addHistogram(fight, 1.0, cells, saturated);
        for (var histogram = 0; histogram < cells.length; histogram++) {
            var sum = 0.0;
            for (var bin = 0; bin < StyleMemory.BINS; bin++) {
                sum += cells[histogram][bin];
            }
            mass[histogram] = scale(sum, 1.0, saturated);
            quantize(cells[histogram], sum, shape[histogram]);
        }
        return new Histogram(mass, shape);
    }

    private static void addHistogram(Optional<StyleMemory> source, double gamma, double[][] cells, int[] saturated) {
        if (source.isEmpty()) {
            return;
        }
        var style = source.get();
        for (var histogram = 0; histogram < StyleMemory.HISTOGRAMS; histogram++) {
            var shape = style.histogramShape(histogram);
            var weight = style.histogramMass(histogram) * gamma;
            for (var bin = 0; bin < shape.length; bin++) {
                cells[histogram][bin] += weight * (shape[bin] & 0xFF) / 255.0;
            }
        }
    }

    private static void quantize(double[] cells, double sum, byte[] shape) {
        if (sum <= 0.0) {
            return;
        }
        var leftover = 255;
        var fraction = new double[cells.length];
        for (var bin = 0; bin < cells.length; bin++) {
            var exact = cells[bin] / sum * 255.0;
            var whole = (int) Math.floor(exact);
            shape[bin] = (byte) whole;
            fraction[bin] = exact - whole;
            leftover -= whole;
        }
        while (leftover > 0) {
            var best = -1;
            for (var bin = 0; bin < fraction.length; bin++) {
                if (fraction[bin] < 0d) {
                    continue;
                }
                if (best < 0 || fraction[bin] > fraction[best]) {
                    best = bin;
                }
            }
            if (best < 0) {
                break;
            }
            shape[best] = (byte) ((shape[best] & 0xFF) + 1);
            fraction[best] = -1d;
            leftover--;
        }
    }

    private static float[] reaction(Optional<StyleMemory> stored, Optional<StyleMemory> fight, double gamma) {
        double weight = stored.map(style -> style.reactionWeight() * gamma).orElse(0d);
        double mean = stored.map(style -> (double) style.reactionMean()).orElse(0d);
        double moment = stored.map(style -> style.reactionM2() * gamma).orElse(0d);
        if (fight.isPresent() && fight.get().reactionWeight() > 0f) {
            var added = PlayerMemoryMath.welford(weight, mean, moment, fight.get().reactionWeight(),
                    fight.get().reactionMean(), fight.get().reactionM2());
            return new float[] {(float) added[0], (float) added[1], (float) added[2]};
        }
        return new float[] {(float) weight, (float) mean, (float) moment};
    }

    private static Episode episodes(Optional<StyleMemory> stored, Optional<StyleMemory> fight, double gamma,
                                    int[] saturated) {
        var counts = new float[StyleMemory.EPISODES];
        var seconds = new float[StyleMemory.EPISODES];
        addEpisodes(stored, gamma, counts, seconds, saturated);
        addEpisodes(fight, 1.0, counts, seconds, saturated);
        return new Episode(counts, seconds);
    }

    private static void addEpisodes(Optional<StyleMemory> source, double gamma, float[] counts, float[] seconds,
                                    int[] saturated) {
        if (source.isEmpty()) {
            return;
        }
        var style = source.get();
        var left = style.episodeCounts();
        var right = style.episodeSeconds();
        for (var index = 0; index < counts.length; index++) {
            counts[index] = scale(counts[index] + left[index] * gamma, 1.0, saturated);
            seconds[index] = scale(seconds[index] + right[index] * gamma, 1.0, saturated);
        }
    }

    private record Transition(int[] keys, float[] weights, float total) {
    }

    private record Histogram(float[] mass, byte[][] shapes) {
    }

    private record Episode(float[] counts, float[] seconds) {
    }
}
