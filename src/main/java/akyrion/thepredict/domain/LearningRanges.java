package akyrion.thepredict.domain;

/**
 * Range checks of {@link LearningSettings}. Split out so each method stays short (rule C10).
 */
final class LearningRanges {
    private LearningRanges() {
    }

    static void checkIdentity(String scope, CandidateScoring base, int version, String voi, String hint) {
        if (!"bench".equals(scope) && !"all".equals(scope)) {
            throw new IllegalArgumentException("learning.scope must be bench or all");
        }
        if (base != CandidateScoring.PRIORITY && base != CandidateScoring.UTILITY) {
            throw new IllegalArgumentException("learning.baseScoring must be priority or utility");
        }
        if (version != 2) {
            throw new IllegalArgumentException("learning.contextVersion must be 2");
        }
        if (!"off".equals(voi) && !"shadow".equals(voi) && !"on".equals(voi)) {
            throw new IllegalArgumentException("learning.voiSkip must be off, shadow or on");
        }
        if (!"off".equals(hint) && !"shadow".equals(hint)) {
            throw new IllegalArgumentException("learning.jevHint must be off or shadow");
        }
    }

    static void checkCounts(int keys, int windows, int minWindows, int halfLife, int weeks, int entries, int spread,
                            int chunk) {
        range(keys, 64, 4096, "learning.maxContextKeysPerProfile");
        range(windows, 8, 128, "learning.maxOpenWindowsPerSession");
        range(minWindows, 5, 200, "learning.minWindowsForValue");
        range(halfLife, 3, 90, "learning.halfLifeDays");
        range(weeks, 2, 26, "learning.weeksKept");
        range(entries, 1000, 100_000, "learning.maxEntries");
        range(spread, 0, 200, "learning.minValueSpreadMillis");
        range(chunk, 200, 10_000, "learning.buildChunkRows");
    }

    static void checkRates(double credit, double group, double decay, double agreement, double gain) {
        within(credit, 0.0, 1.0, "learning.creditShare");
        within(group, 0.0, 1.0, "learning.groupCreditShare");
        within(decay, 0.5, 1.0, "learning.creditDecayPerWindow");
        within(agreement, 0.5, 0.99, "learning.voi.minAgreement");
        within(gain, 0.0, 0.2, "learning.voi.maxExpectedGain");
    }

    static void checkCalibration(int decisions, int prior, int drift, int voiWindows, int skips) {
        range(decisions, 24, 1000, "learning.calibration.minDecisions");
        range(prior, 0, 200, "learning.calibration.priorStrength");
        range(drift, 100, 5000, "learning.calibration.driftReferenceWindows");
        range(voiWindows, 20, 1000, "learning.voi.minWindows");
        range(skips, 1, 10, "learning.voi.maxConsecutiveSkips");
    }

    private static void range(int value, int low, int high, String path) {
        if (value < low || value > high) {
            throw new IllegalArgumentException(path + " out of range");
        }
    }

    private static void within(double value, double low, double high, String path) {
        if (!(value >= low && value <= high)) {
            throw new IllegalArgumentException(path + " out of range");
        }
    }
}
