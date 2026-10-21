package akyrion.thepredict.domain;

import akyrion.thepredict.domain.learn.CalibratedMinimum;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import akyrion.thepredict.domain.learn.ExploreSettings;
import akyrion.thepredict.domain.learn.TrainingSource;

import java.util.List;

/**
 * The {@code learning} section (M27 allow-unverified, extended by M28). Defaults live only here (rule C13).
 * The one-argument constructor keeps every M27 call site and fills the M28 defaults (rule W5).
 *
 * @param allowUnverified whether an unverified mode may pass the gate
 * @param collect whether windows are recorded
 * @param scope {@code bench} or {@code all}
 * @param baseScoring base of a learned score; never {@link CandidateScoring#LEARNED}
 * @param trainingSources sources that train
 * @param contextVersion always 2
 * @param maxContextKeysPerProfile fine-key cap
 * @param maxOpenWindowsPerSession open credit windows
 * @param creditShare η
 * @param groupCreditShare g
 * @param creditDecay β
 * @param minWindowsForValue SPIBB minimum
 * @param halfLifeDays week decay
 * @param weeksKept retention
 * @param maxEntries table cap
 * @param minValueSpreadMillis spread that may reorder
 * @param buildChunkRows rows per build slice
 * @param exploreInDuels entity duels may explore
 * @param exploration epsilon and max share
 * @param calibrationMinDecisions labelled decisions
 * @param priorStrength Platt prior
 * @param driftReferenceWindows reference windows
 * @param voiSkip {@code off}, {@code shadow} or {@code on}
 * @param voiMinWindows usable cell size
 * @param voiMinAgreement Wilson lower bound
 * @param voiMaxExpectedGain gain cap
 * @param voiMaxConsecutiveSkips skip cap
 * @param jevHint {@code off} or {@code shadow}
 */
public record LearningSettings(boolean allowUnverified, boolean collect, String scope, CandidateScoring baseScoring,
                               List<TrainingSource> trainingSources, int contextVersion, int maxContextKeysPerProfile,
                               int maxOpenWindowsPerSession, double creditShare, double groupCreditShare,
                               double creditDecay, int minWindowsForValue, int halfLifeDays, int weeksKept,
                               int maxEntries, int minValueSpreadMillis, int buildChunkRows, boolean exploreInDuels,
                               ExploreSettings exploration, int calibrationMinDecisions, int priorStrength,
                               int driftReferenceWindows, String voiSkip, int voiMinWindows, double voiMinAgreement,
                               double voiMaxExpectedGain, int voiMaxConsecutiveSkips, String jevHint) {
    /** A fresh installation. */
    public static final LearningSettings DEFAULT = new LearningSettings(false);

    /**
     * M27 constructor. Every other key takes the M28 default.
     *
     * @param allowUnverified whether an unverified mode may pass the gate
     */
    public LearningSettings(boolean allowUnverified) {
        this(allowUnverified, true, "bench", CandidateScoring.PRIORITY, TrainingSource.DEFAULTS, 2, 512, 32, 0.5, 0.5,
                0.8, 20, 14, 8, 20_000, 20, 2_000, false, ExploreSettings.DEFAULT, 48, 20, 500, "off", 50, 0.90, 0.02,
                2, "off");
    }

    /**
     * Copies the source list and checks ranges.
     */
    public LearningSettings {
        trainingSources = List.copyOf(trainingSources);
        LearningRanges.checkIdentity(scope, baseScoring, contextVersion, voiSkip, jevHint);
        LearningRanges.checkCounts(maxContextKeysPerProfile, maxOpenWindowsPerSession, minWindowsForValue,
                halfLifeDays, weeksKept, maxEntries, minValueSpreadMillis, buildChunkRows);
        LearningRanges.checkRates(creditShare, groupCreditShare, creditDecay, voiMinAgreement, voiMaxExpectedGain);
        LearningRanges.checkCalibration(calibrationMinDecisions, priorStrength, driftReferenceWindows, voiMinWindows,
                voiMaxConsecutiveSkips);
    }

    /**
     * The confidence gate is a decision key, not a learning key. It is named here so callers can see the default.
     *
     * @return {@link ConfidenceGate#RAW}
     */
    public ConfidenceGate confidenceGate() {
        return ConfidenceGate.DEFAULT;
    }

    /**
     * The calibrated floor is a decision key.
     *
     * @return parity
     */
    public CalibratedMinimum calibratedMinimum() {
        return CalibratedMinimum.PARITY;
    }
}
