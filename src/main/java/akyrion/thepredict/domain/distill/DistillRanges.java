package akyrion.thepredict.domain.distill;

import java.util.List;

/**
 * Ranges of {@link DistillSettings} (rule C13).
 */
public final class DistillRanges {
    private DistillRanges() {
    }

    /**
     * Checks one settings value.
     *
     * @param scope {@code bench} or {@code all}
     * @param minCellOffers minimum offers before a fine cell is usable
     * @param maxTreeDepth maximum tree depth
     * @param maxLeaves maximum leaves
     * @param minLeafDecisions minimum decisions in a leaf
     * @param overrideZ z of the local-order override
     * @param minCellDecisions minimum decisions before coverage
     * @param nonInferiorityMargin outcome margin
     * @param coverageRefreshMinutes refresh period
     * @param coverageMaxAgeMinutes maximum age of a cell
     * @param auditEvery audit period
     * @param assistMinDelegable minimum delegable cells for assist
     * @param openingSeconds opening of a regime
     * @param dwellTicks dwell before a regime switch
     * @param maxSwitchesPerEncounter switch cap
     * @param minGain minimum split gain
     * @param minDecisions minimum decisions of a split
     * @param extraDimensions extra regime dimensions
     * @param maxRegimesPerProfile regime cap
     * @param maxArtifactBytes artifact byte cap
     * @param weeksKept weeks kept
     * @param cusumH CUSUM threshold
     */
    public static void check(String scope, int minCellOffers, int maxTreeDepth, int maxLeaves, int minLeafDecisions,
                             int overrideZ, int minCellDecisions, double nonInferiorityMargin,
                             int coverageRefreshMinutes, int coverageMaxAgeMinutes, int auditEvery,
                             int assistMinDelegable, int openingSeconds, int dwellTicks, int maxSwitchesPerEncounter,
                             double minGain, int minDecisions, List<String> extraDimensions, int maxRegimesPerProfile,
                             int maxArtifactBytes, int weeksKept, int cusumH) {
        if (!"bench".equals(scope) && !"all".equals(scope)) {
            throw new IllegalArgumentException("distill.scope");
        }
        range(minCellOffers, 2, 100, "distill.minCellOffers");
        range(maxTreeDepth, 2, 10, "distill.maxTreeDepth");
        range(maxLeaves, 4, 512, "distill.maxLeaves");
        range(minLeafDecisions, 5, 500, "distill.minLeafDecisions");
        range(overrideZ, 1, 4, "distill.overrideZ");
        range(minCellDecisions, 20, 1000, "distill.minCellDecisions");
        if (nonInferiorityMargin < 0.0 || nonInferiorityMargin > 0.10) {
            throw new IllegalArgumentException("distill.nonInferiorityMargin");
        }
        range(coverageRefreshMinutes, 1, 120, "distill.coverageRefreshMinutes");
        range(coverageMaxAgeMinutes, 10, 1440, "distill.coverageMaxAgeMinutes");
        range(auditEvery, 2, 100, "distill.auditEvery");
        range(assistMinDelegable, 100, 10_000, "distill.assistMinDelegable");
        range(openingSeconds, 3, 60, "distill.regime.openingSeconds");
        range(dwellTicks, 10, 200, "distill.regime.dwellTicks");
        range(maxSwitchesPerEncounter, 1, 20, "distill.regime.maxSwitchesPerEncounter");
        if (minGain < 0.0 || minGain > 0.2) {
            throw new IllegalArgumentException("distill.regime.minGain");
        }
        range(minDecisions, 200, 50_000, "distill.regime.minDecisions");
        range(maxRegimesPerProfile, 1, 4, "distill.maxRegimesPerProfile");
        range(maxArtifactBytes, 4096, 1_048_576, "distill.maxArtifactBytes");
        range(weeksKept, 2, 26, "distill.weeksKept");
        range(cusumH, 2, 10, "distill.drift.cusumH");
        for (var extra : extraDimensions) {
            if (!extra.equals("zone") && !extra.equals("group") && !extra.equals("pattern")) {
                throw new IllegalArgumentException("distill.regime.extraDimensions");
            }
        }
    }

    private static void range(int value, int low, int high, String path) {
        if (value < low || value > high) {
            throw new IllegalArgumentException(path);
        }
    }
}
