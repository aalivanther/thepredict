package akyrion.thepredict.adapter.server.goals;

import java.util.List;
import java.util.Objects;

/**
 * Base AI lines chosen by {@link BaseAiResolver}, with {@code AIGoalSelectors} syntax and their original
 * priorities; {@link GoalLineGenerator} shifts them. Immutable.
 *
 * @param lines non-blank lines without {@code clear}, in their original order; never empty
 * @param origin where the lines come from
 */
public record BaseAiSource(List<String> lines, BaseAiOrigin origin) {
    /**
     * Validates the source and copies the lines.
     *
     * @throws NullPointerException if a component or line is {@code null}
     * @throws IllegalArgumentException if there is no line, a line is blank or a line is {@code clear}
     */
    public BaseAiSource {
        lines = List.copyOf(lines);
        Objects.requireNonNull(origin, "origin");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("base AI needs at least one line");
        }
        for (var line : lines) {
            if (line.isBlank() || BaseAiLines.isClear(line)) {
                throw new IllegalArgumentException("base AI lines must not be blank or clear");
            }
        }
    }
}
