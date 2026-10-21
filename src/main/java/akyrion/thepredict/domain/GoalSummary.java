package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Description of one mob goal for inspection, without Paper types. Immutable.
 *
 * @param key goal key text; not blank
 * @param types goal types such as {@code MOVE} or {@code LOOK}
 * @param running whether the goal is running
 */
public record GoalSummary(String key, Set<String> types, boolean running) {
    /**
     * Validates the summary and copies the types.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if the key is blank
     */
    public GoalSummary {
        DomainChecks.requireText(key, "key");
        types = Set.copyOf(Objects.requireNonNull(types, "types"));
    }
}
