package akyrion.thepredict.domain.distill;

import java.util.List;

/**
 * The {@code distill} section (M29). Defaults live only here (rule C13).
 *
 * @param mode global mode
 * @param scope {@code bench} or {@code all}
 * @param collect whether teaching rows are recorded
 * @param minCellOffers offers before a fine cell is stored
 * @param maxTreeDepth tree depth
 * @param maxLeaves leaf cap
 * @param minLeafDecisions decisions per leaf
 * @param overrideZ SPIBB z
 * @param minCellDecisions coverage minimum
 * @param nonInferiorityMargin outcome margin
 * @param coverageRefreshMinutes refresh period
 * @param coverageMaxAgeMinutes stale age
 * @param auditEvery audit period
 * @param assistMinDelegable shadow-to-assist gate
 * @param jevForOod whether autonomous asks Jev outside coverage
 * @param openingSeconds regime opening
 * @param dwellTicks regime dwell
 * @param maxSwitchesPerEncounter switch cap
 * @param minGain regime gain
 * @param minDecisions regime decisions
 * @param extraDimensions zone, group or pattern
 * @param maxRegimesPerProfile regime cap
 * @param maxArtifactBytes artifact cap
 * @param weeksKept retention
 * @param cusumH drift threshold
 */
public record DistillSettings(DistillMode mode, String scope, boolean collect, int minCellOffers, int maxTreeDepth,
                              int maxLeaves, int minLeafDecisions, int overrideZ, int minCellDecisions,
                              double nonInferiorityMargin, int coverageRefreshMinutes, int coverageMaxAgeMinutes,
                              int auditEvery, int assistMinDelegable, boolean jevForOod, int openingSeconds,
                              int dwellTicks, int maxSwitchesPerEncounter, double minGain, int minDecisions,
                              List<String> extraDimensions, int maxRegimesPerProfile, int maxArtifactBytes,
                              int weeksKept, int cusumH) {
    /** A fresh installation. Mode is off, so the capture path does not change. */
    public static final DistillSettings DEFAULT = new DistillSettings(DistillMode.OFF, "bench", true, 8, 6, 64, 20, 2,
            50, 0.02, 10, 60, 10, 500, true, 10, 40, 6, 0.02, 2000, List.of(), 4, 65536, 8, 5);

    /**
     * Copies the dimensions and checks ranges.
     */
    public DistillSettings {
        extraDimensions = List.copyOf(extraDimensions);
        DistillRanges.check(scope, minCellOffers, maxTreeDepth, maxLeaves, minLeafDecisions, overrideZ,
                minCellDecisions, nonInferiorityMargin, coverageRefreshMinutes, coverageMaxAgeMinutes, auditEvery,
                assistMinDelegable, openingSeconds, dwellTicks, maxSwitchesPerEncounter, minGain, minDecisions,
                extraDimensions, maxRegimesPerProfile, maxArtifactBytes, weeksKept, cusumH);
    }

    /**
     * Same limits with another global mode.
     *
     * @param next the mode
     * @return the copy
     */
    public DistillSettings withMode(DistillMode next) {
        return new DistillSettings(next, scope, collect, minCellOffers, maxTreeDepth, maxLeaves, minLeafDecisions,
                overrideZ, minCellDecisions, nonInferiorityMargin, coverageRefreshMinutes, coverageMaxAgeMinutes,
                auditEvery, assistMinDelegable, jevForOod, openingSeconds, dwellTicks, maxSwitchesPerEncounter,
                minGain, minDecisions, extraDimensions, maxRegimesPerProfile, maxArtifactBytes, weeksKept, cusumH);
    }

    /**
     * Same limits with another out-of-coverage flag.
     *
     * @param next whether autonomous asks Jev outside coverage
     * @return the copy
     */
    public DistillSettings withJevForOod(boolean next) {
        return new DistillSettings(mode, scope, collect, minCellOffers, maxTreeDepth, maxLeaves, minLeafDecisions,
                overrideZ, minCellDecisions, nonInferiorityMargin, coverageRefreshMinutes, coverageMaxAgeMinutes,
                auditEvery, assistMinDelegable, next, openingSeconds, dwellTicks, maxSwitchesPerEncounter,
                minGain, minDecisions, extraDimensions, maxRegimesPerProfile, maxArtifactBytes, weeksKept, cusumH);
    }

    /**
     * Bench scope does not act while a player is the target.
     *
     * @param playerPresent whether the mob's target is a player
     * @return {@code true} when delegation must stay in shadow
     */
    public boolean benchWithholds(boolean playerPresent) {
        return "bench".equals(scope) && playerPresent;
    }

    /**
     * Autonomous without {@code jevForOod} must not open a client call outside coverage.
     *
     * @param covered whether the fine cell is covered
     * @return {@code true} when the question is resolved locally
     */
    public boolean suppressClient(boolean covered) {
        return mode == DistillMode.AUTONOMOUS && !covered && !jevForOod;
    }
}
