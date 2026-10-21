package akyrion.thepredict.domain.learn;

import java.util.Optional;

/**
 * Whether Jev may be skipped (M28.8). Shadow records {@code wouldSkip} and still asks.
 *
 * @param skip whether the live switch would skip
 * @param wouldSkip whether the conditions hold, even in shadow
 * @param cause the cause when skipping, or empty
 */
public record VoiDecision(boolean skip, boolean wouldSkip, Optional<LearnCause> cause) {
    /** Ask Jev. */
    public static final VoiDecision ASK = new VoiDecision(false, false, Optional.empty());

    /**
     * Copies the cause.
     */
    public VoiDecision {
        cause = cause == null ? Optional.empty() : cause;
    }
}
