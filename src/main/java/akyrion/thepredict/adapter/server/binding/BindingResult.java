package akyrion.thepredict.adapter.server.binding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of binding: the profiles ready for mobs with {@code Predictor: true} and every problem found.
 *
 * @param profiles bound profiles keyed by exact MythicMobs mob ID, in input order
 * @param problems errors and warnings
 */
public record BindingResult(Map<String, BoundProfile> profiles, List<BindingProblem> problems) {
    /**
     * Copies the collections, keeping the order of {@code profiles}.
     *
     * @throws NullPointerException if a component or element is {@code null}
     */
    public BindingResult {
        profiles = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(profiles, "profiles")));
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
    }
}
