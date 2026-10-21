package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * One visible stance change (M23.10). Recorded at {@code FULL}.
 *
 * @param from stance before the step
 * @param to stance after the step
 * @param base base after the step
 * @param action action after the step
 * @param enraged whether the enraged mark is on after the step
 * @param cause why it changed
 * @param dwellTicks ticks the base had already been held
 * @param nativeMode mode that decided the write
 * @param nativeStance author stance mirror, {@code none} when it was not read
 * @param variableWritten whether {@code predictor_stance} was written
 * @param nativeWritten whether the native stance was written
 */
public record StanceChange(CombatStance from, CombatStance to, BaseStance base, ActionStance action, boolean enraged,
                           StanceCause cause, long dwellTicks, StanceNativeMode nativeMode, String nativeStance,
                           boolean variableWritten, boolean nativeWritten) {
    /**
     * Validates the change.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the dwell is negative
     */
    public StanceChange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(nativeMode, "nativeMode");
        Objects.requireNonNull(nativeStance, "nativeStance");
        if (dwellTicks < 0) {
            throw new IllegalArgumentException("dwellTicks must be >= 0, was " + dwellTicks);
        }
    }
}
