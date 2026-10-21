package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What one choice declares about its tell (M23.2). Empty parts are derived.
 *
 * @param danger declared class, empty to derive it
 * @param answer declared answer, empty to derive it
 * @param recoveryTicks declared recovery, empty to read it or use the default
 */
public record ChoiceTelegraph(Optional<DangerClass> danger, Optional<TellAnswer> answer, OptionalInt recoveryTicks) {
    /** Nothing declared. */
    public static final ChoiceTelegraph NONE = new ChoiceTelegraph(Optional.empty(), Optional.empty(), OptionalInt.empty());

    /**
     * Validates the declaration.
     *
     * @throws NullPointerException if a component is null
     * @throws IllegalArgumentException if recovery is outside 0–100
     */
    public ChoiceTelegraph {
        Objects.requireNonNull(danger, "danger");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(recoveryTicks, "recoveryTicks");
        recoveryTicks.ifPresent(ticks -> DomainChecks.requireRange(ticks, 0, SkillPurpose.MAX_WIND_UP_TICKS,
                "recoveryTicks"));
    }
}
