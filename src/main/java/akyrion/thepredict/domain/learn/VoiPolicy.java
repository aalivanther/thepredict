package akyrion.thepredict.domain.learn;

import java.util.Optional;

/**
 * Value-of-information skip (M28.8). Every condition is required. The first capture, a reactive visit after a
 * signature change, a contingency and g0 never skip. The parity exception of rule 8 does not apply.
 */
public final class VoiPolicy {
    private VoiPolicy() {
    }

    /**
     * Evaluates a skip. {@code on} is required for {@code skip}; shadow still sets {@code wouldSkip}.
     *
     * @param pass artifact verdict allows the scope
     * @param mode {@code off}, {@code shadow} or {@code on}
     * @param usable the best cell has enough windows
     * @param gap value gap
     * @param sdBest standard deviation of the best
     * @param sdSecond standard deviation of the second
     * @param jevSeen fine-cell questions, or the coarse fallback
     * @param jevAgree agreements in that cell
     * @param minAgreement required Wilson lower bound
     * @param disagreementDelta stored per coarse cell
     * @param maxGain maximum expected gain
     * @param consecutiveSkips skips already taken
     * @param maxSkips cap
     * @param forbidden first capture, reactive signature change, contingency or g0
     * @return the decision
     */
    public static VoiDecision evaluate(boolean pass, String mode, boolean usable, double gap, double sdBest,
                                       double sdSecond, int jevSeen, int jevAgree, double minAgreement,
                                       double disagreementDelta, double maxGain, int consecutiveSkips, int maxSkips,
                                       boolean forbidden) {
        if (!pass || "off".equals(mode) || forbidden || !usable || consecutiveSkips >= maxSkips) {
            return VoiDecision.ASK;
        }
        if (gap < 2.0 * StrictMath.sqrt(sdBest * sdBest + sdSecond * sdSecond)) {
            return VoiDecision.ASK;
        }
        if (jevSeen < 40) {
            return VoiDecision.ASK;
        }
        var agreement = WilsonMath.lower(jevAgree, jevSeen);
        if (agreement < minAgreement) {
            return VoiDecision.ASK;
        }
        var gain = (1.0 - agreement) * Math.max(0.0, disagreementDelta);
        if (gain > maxGain) {
            return VoiDecision.ASK;
        }
        var live = "on".equals(mode);
        return new VoiDecision(live, true, live ? Optional.of(LearnCause.VOI_SKIP) : Optional.empty());
    }
}
