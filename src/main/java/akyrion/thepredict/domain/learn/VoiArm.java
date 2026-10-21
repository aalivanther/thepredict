package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.TacticId;

import java.util.Objects;

/**
 * Value-of-information inputs for one capture (M28.8). {@link #OFF} asks Jev.
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
 * @param tactic tactic held when a live skip drops the question
 * @param captureSignature signature of the capture
 * @param holdTicks how long the held intent lasts
 */
public record VoiArm(boolean pass, String mode, boolean usable, double gap, double sdBest, double sdSecond,
                     int jevSeen, int jevAgree, double minAgreement, double disagreementDelta, double maxGain,
                     int consecutiveSkips, int maxSkips, boolean forbidden, TacticId tactic, int captureSignature,
                     long holdTicks) {
    /** The default. {@code voiSkip} stays off. */
    public static final VoiArm OFF = new VoiArm(false, "off", false, 0.0, 0.0, 0.0, 0, 0, 1.0, 0.0, 0.0, 0, 0, true,
            new TacticId("none"), 0, 0L);

    /**
     * Validates the arm.
     */
    public VoiArm {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(tactic, "tactic");
    }
}
