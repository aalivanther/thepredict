package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One tactic a profile offers: a movement and, optionally, a skill choice. Immutable.
 *
 * <p>The rule that {@code NONE} is the only movement for species with a {@code Brain} is applied by
 * the binder, because the domain does not know species.
 *
 * @param id semantic tactic name
 * @param movement movement the tactic asks for
 * @param choice skill choice cast while the tactic is active; required for {@code FLANK}
 * @param description optional author text for the model
 * @param priority author priority from {@value #MIN_PRIORITY} to {@value #MAX_PRIORITY}
 * @param stance base stance the tactic declares; empty uses the movement table (M23.2)
 */
public record Tactic(TacticId id, Movement movement, Optional<ChoiceId> choice,
                     Optional<String> description, int priority, Optional<BaseStance> stance) {
    /** Lowest priority. */
    public static final int MIN_PRIORITY = 0;
    /** Highest priority. */
    public static final int MAX_PRIORITY = 100;

    /**
     * Validates the tactic.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the priority is out of range, the description is blank
     *         or {@code FLANK} has no choice
     */
    public Tactic {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(stance, "stance");
        description.ifPresent(text -> DomainChecks.requireText(text, "description"));
        DomainChecks.requireRange(priority, MIN_PRIORITY, MAX_PRIORITY, "priority");
        if (movement == Movement.FLANK && choice.isEmpty()) {
            throw new IllegalArgumentException("choice is required for movement FLANK in tactic " + id);
        }
    }

    /**
     * A tactic without a declared stance (rule W5).
     *
     * @param id semantic tactic name
     * @param movement movement the tactic asks for
     * @param choice skill choice
     * @param description optional author text
     * @param priority author priority
     */
    public Tactic(TacticId id, Movement movement, Optional<ChoiceId> choice, Optional<String> description,
                  int priority) {
        this(id, movement, choice, description, priority, Optional.empty());
    }
}
