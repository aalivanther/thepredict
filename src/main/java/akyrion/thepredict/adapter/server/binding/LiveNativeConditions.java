package akyrion.thepredict.adapter.server.binding;

import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.conditions.CustomCondition;

import java.util.List;
import java.util.Objects;

/**
 * Converted conditions backed by live MythicMobs objects. Server thread only.
 *
 * @param lines author lines
 * @param conditions converted conditions of the valid lines
 * @param invalidLines lines MythicMobs rejected
 */
record LiveNativeConditions(List<String> lines, List<SkillCondition> conditions, List<String> invalidLines)
        implements NativeConditions {
    LiveNativeConditions {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
        invalidLines = List.copyOf(Objects.requireNonNull(invalidLines, "invalidLines"));
    }

    @Override
    public List<String> unresolvedLines() {
        return conditions.stream()
                .filter(condition -> condition instanceof CustomCondition custom && custom.getCondition().isEmpty())
                .map(SkillCondition::getLine)
                .toList();
    }
}
