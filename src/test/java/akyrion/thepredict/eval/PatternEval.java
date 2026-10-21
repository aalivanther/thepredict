package akyrion.thepredict.eval;

import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.engine.duel.PatternScript;

/**
 * Reads script labels and scores a pattern sample (M26.10). Production code does not.
 */
public final class PatternEval {
    private PatternEval() {
    }

    /**
     * How often the script shows, and its label.
     *
     * @param script the layer
     * @param samples draws
     * @param seed the seed
     * @return label, frequency
     */
    public static String frequency(PatternScript script, int samples, long seed) {
        var hits = 0;
        for (var index = 0; index < samples; index++) {
            if (script.shows(StableRandom.mix(seed ^ index))) {
                hits++;
            }
        }
        return script.label() + " " + (hits / (double) samples);
    }

    /**
     * Precision of the predictions that fired.
     *
     * @param truePositives hits that were real
     * @param predictedPositives hits the detector published
     * @return the ratio, or zero when nothing was published
     */
    public static double precision(int truePositives, int predictedPositives) {
        return predictedPositives == 0 ? 0.0 : truePositives / (double) predictedPositives;
    }

    /**
     * Recall against the labelled occasions.
     *
     * @param truePositives hits that were real
     * @param labelled labelled occasions
     * @return the ratio, or zero when there were none
     */
    public static double recall(int truePositives, int labelled) {
        return labelled == 0 ? 0.0 : truePositives / (double) labelled;
    }
}
