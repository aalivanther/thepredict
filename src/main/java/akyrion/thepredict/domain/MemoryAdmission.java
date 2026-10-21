package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What one admission may use (M27.5, errata E27-5 applied). The fact is fixed here and is never recomputed mid-fight.
 *
 * @param cause why the admission looks like this
 * @param startRung rung handed to the fight
 * @param rungCause cause appended when memory moved the rung, empty otherwise
 * @param fact the one history phrase, empty when the mode or the profile forbids it
 * @param seededPatterns Beta patterns that received a seed
 * @param seedTrialsTotal seed mass across those patterns
 * @param mode the mode that applied
 */
public record MemoryAdmission(MemoryAdmissionCause cause, int startRung, Optional<PressureCause> rungCause,
                              Optional<MemoryFact> fact, int seededPatterns, double seedTrialsTotal, MemoryMode mode) {
    /**
     * Requires every reference component (rules C2 and C4): an absent cause or fact is an empty optional, never
     * {@code null}.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public MemoryAdmission {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(rungCause, "rungCause");
        Objects.requireNonNull(fact, "fact");
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * The same admission without its history phrase, for a profile whose {@code Memory.Facts} is off
     * ({@link MemoryProfile#factShown}). Seeds and the rung stay.
     *
     * @return the copy
     */
    public MemoryAdmission withoutFact() {
        return new MemoryAdmission(cause, startRung, rungCause, Optional.empty(), seededPatterns, seedTrialsTotal,
                mode);
    }
}
