package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The signal {@code PREDICTOR_PATTERN_<PATTERN>} (M26.6, M26.11, DOC-01): one per activation, that is, each entry into
 * {@link PatternState#ACTIVE}, by its evidence or at the end of a rest. It goes only where the vocabulary shows the
 * patterns ({@code bias} and {@code full}), so a mode that must not change decisions does not move the author's skills
 * either. Resting, leaving and a halved changepoint send nothing. The adapter sends the signal only to a mob that
 * handles it.
 */
public final class PatternSignals {
    private PatternSignals() {
    }

    /**
     * Whether a recorded change is an activation.
     *
     * @param cause why the change was recorded
     * @param state the state after the change
     * @return {@code true} for an entry into {@link PatternState#ACTIVE}
     */
    public static boolean activation(PatternCause cause, PatternState state) {
        Objects.requireNonNull(cause, "cause");
        return Objects.requireNonNull(state, "state") == PatternState.ACTIVE
                && (cause == PatternCause.ENTER || cause == PatternCause.REST_END);
    }

    /**
     * The signal of one activation, as one member sees it.
     *
     * @param pattern the pattern that became active
     * @param mode effective pattern mode of the member
     * @return the request, or empty where the mode does not show the patterns
     */
    public static Optional<CueRequest> of(EnemyPattern pattern, PatternMode mode) {
        Objects.requireNonNull(pattern, "pattern");
        if (Objects.requireNonNull(mode, "mode") != PatternMode.BIAS && mode != PatternMode.FULL) {
            return Optional.empty();
        }
        return Optional.of(CueRequest.pattern(pattern));
    }
}
