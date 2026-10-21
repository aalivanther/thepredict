package akyrion.thepredict.domain;

/**
 * Global pattern limits (M26.11). Every default lives here and only here (rule C13). Thresholds, occasions and
 * counters are {@link PatternRules} and {@link CounterMap}, not keys.
 *
 * @param mode delivered mode
 * @param hearingBlocks audible range
 * @param sampleTicks ticks between kinematic samples
 * @param maxWatchedPerEncounter enemies read every sample
 * @param maxReadsPerTick global read cap
 * @param strafeBlocks movement that counts as a strafe
 * @param airSwings what an air swing does
 * @param mergeTicks window that merges the same strafe or step
 * @param idleTicks ticks before an idle token
 * @param maxTokensPerSecond token bucket
 * @param order2Contexts order-2 rows
 * @param forgetting predictor decay
 * @param evidenceForgetting beta decay
 * @param confidenceQuantile tail probability
 * @param minTrials trials before a pattern can activate
 * @param persistUpdates consecutive tails required
 * @param exitMargin gap below theta that deactivates
 * @param changepointH CUSUM threshold
 * @param forecastMinAccuracy minimum running accuracy
 * @param histogramBinTicks reaction bins
 * @param maxMicrosPerTick budget of the pattern step
 * @param maxExploitShare quota against a player
 * @param maxCounterDelayTicks longest counter delay
 * @param exploitWindowTicks window of the quota
 * @param restAfterSuccesses motivated hits that open a rest
 * @param restTicks length of a rest
 * @param maxExploitedPatterns patterns biased at once
 * @param factOnTicks ticks a fact must hold
 * @param factOffTicks ticks a fact must be gone
 * @param maxFactsPerParticipant facts of one enemy
 * @param maxFactsPerRequest facts of one question
 */
public record PatternSettings(PatternMode mode, int hearingBlocks, int sampleTicks, int maxWatchedPerEncounter,
                              int maxReadsPerTick, double strafeBlocks, AirSwingPolicy airSwings, int mergeTicks,
                              int idleTicks, int maxTokensPerSecond, int order2Contexts, double forgetting,
                              double evidenceForgetting, double confidenceQuantile, int minTrials,
                              int persistUpdates, double exitMargin, double changepointH, double forecastMinAccuracy,
                              int histogramBinTicks, int maxMicrosPerTick, double maxExploitShare,
                              int maxCounterDelayTicks, int exploitWindowTicks, int restAfterSuccesses, int restTicks,
                              int maxExploitedPatterns, int factOnTicks, int factOffTicks, int maxFactsPerParticipant,
                              int maxFactsPerRequest) {
    /** Delivered limits. The mode observes and does not tilt decisions. */
    public static final PatternSettings DEFAULT = new PatternSettings(PatternMode.OBSERVE, 16, 2, 4, 16, 0.15,
            AirSwingPolicy.OBSERVE, 6, 20, 20, 32, 0.98, 0.98, 0.05, 8, 3, 0.05, 4.0, 0.4, 2, 60, 0.5, 20, 400, 3,
            300, 2, 40, 80, 2, 4);

    /**
     * Checks every range.
     *
     * @throws IllegalArgumentException when a value is outside its range
     */
    public PatternSettings {
        if (mode == null || airSwings == null) {
            throw new IllegalArgumentException("patterns.mode and patterns.airSwings are required");
        }
        range(hearingBlocks, 4, 32, "patterns.hearingBlocks");
        range(sampleTicks, 1, 10, "patterns.sampleTicks");
        range(maxWatchedPerEncounter, 1, 16, "patterns.maxWatchedPerEncounter");
        range(maxReadsPerTick, 1, 64, "patterns.maxReadsPerTick");
        rangeDouble(strafeBlocks, 0.05, 0.5, "patterns.strafeBlocks");
        range(mergeTicks, 0, 20, "patterns.mergeTicks");
        range(idleTicks, 10, 100, "patterns.idleTicks");
        range(maxTokensPerSecond, 5, 40, "patterns.maxTokensPerSecond");
        range(order2Contexts, 0, 64, "patterns.order2Contexts");
        rangeDouble(forgetting, 0.90, 1.0, "patterns.forgetting");
        rangeDouble(evidenceForgetting, 0.90, 1.0, "patterns.evidenceForgetting");
        rangeDouble(confidenceQuantile, 0.01, 0.20, "patterns.confidenceQuantile");
        range(minTrials, 4, 30, "patterns.minTrials");
        range(persistUpdates, 1, 10, "patterns.persistUpdates");
        rangeDouble(exitMargin, 0.0, 0.2, "patterns.exitMargin");
        rangeDouble(changepointH, 2.0, 8.0, "patterns.changepointH");
        rangeDouble(forecastMinAccuracy, 0.2, 0.9, "patterns.forecastMinAccuracy");
        range(histogramBinTicks, 1, 4, "patterns.histogramBinTicks");
        range(maxMicrosPerTick, 10, 500, "patterns.maxMicrosPerTick");
        rangeDouble(maxExploitShare, 0.0, 1.0, "patterns.maxExploitShare");
        range(maxCounterDelayTicks, 0, 40, "patterns.maxCounterDelayTicks");
        range(exploitWindowTicks, 100, 1200, "patterns.exploitWindowTicks");
        range(restAfterSuccesses, 1, 10, "patterns.restAfterSuccesses");
        range(restTicks, 0, 2400, "patterns.restTicks");
        range(maxExploitedPatterns, 1, 4, "patterns.maxExploitedPatterns");
        range(factOnTicks, 10, 200, "patterns.factOnTicks");
        range(factOffTicks, 20, 400, "patterns.factOffTicks");
        range(maxFactsPerParticipant, 0, 3, "patterns.maxFactsPerParticipant");
        range(maxFactsPerRequest, 0, 6, "patterns.maxFactsPerRequest");
    }

    private static void range(int value, int least, int most, String path) {
        if (value < least || value > most) {
            throw new IllegalArgumentException(path + " must be within [" + least + ", " + most + "], was " + value);
        }
    }

    private static void rangeDouble(double value, double least, double most, String path) {
        if (!(value >= least) || value > most) {
            throw new IllegalArgumentException(path + " must be within [" + least + ", " + most + "], was " + value);
        }
    }
}
