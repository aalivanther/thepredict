package akyrion.thepredict.domain.distill;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.HeldIntent;
import akyrion.thepredict.domain.TacticId;

import java.util.Optional;

/**
 * What a distilled capture does before the value-of-information skip (M29.4).
 *
 * @param delegate whether the student decides
 * @param audit whether this capture is the Jev audit
 * @param held the intent to hold, empty when the question still goes out
 * @param cause why the student decided, empty when it did not
 * @param ood why the cell is outside coverage, empty when covered or off
 */
public record DistillHold(boolean delegate, boolean audit, Optional<HeldIntent> held, Optional<String> cause,
                          Optional<OodCause> ood) {
    /** The capture path is unchanged. */
    public static final DistillHold NONE = new DistillHold(false, false, Optional.empty(), Optional.empty(),
            Optional.empty());
    private static final Optional<String> CELL = Optional.of("DISTILLED_CELL");
    private static final Optional<String> OVERRIDE = Optional.of("DISTILLED_VALUE_OVERRIDE");

    /**
     * Checks the optionals.
     */
    public DistillHold {
        held = held == null ? Optional.empty() : held;
        cause = cause == null ? Optional.empty() : cause;
        ood = ood == null ? Optional.empty() : ood;
    }

    /**
     * A delegated hold.
     *
     * @param tactic the tactic
     * @param signature capture signature
     * @param tick adoption tick
     * @param holdTicks ticks the intent may live
     * @param cause {@code DISTILLED_CELL} or {@code DISTILLED_VALUE_OVERRIDE}
     * @return the hold
     */
    public static DistillHold delegated(String tactic, int signature, long tick, int holdTicks, String cause) {
        var intent = new HeldIntent(TacticId.of(tactic), BehaviorPolicy.DISTILLED, signature, tick, tick + holdTicks);
        var why = "DISTILLED_CELL".equals(cause) ? CELL
                : "DISTILLED_VALUE_OVERRIDE".equals(cause) ? OVERRIDE : Optional.of(cause);
        return new DistillHold(true, false, Optional.of(intent), why, Optional.empty());
    }
}
