package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.GoalSummary;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Lists the goals of a mob as {@link GoalSummary} for {@code MobControl.describeGoals} (M05), which
 * {@code /thepredict inspect} and {@code debug goals} show (M12). Server thread only; bounded to
 * {@link #MAX_GOAL_LINES} goals.
 */
public final class GoalInspector {
    /** Maximum goals listed. */
    public static final int MAX_GOAL_LINES = 40;

    private final MobGoals mobGoals;

    /**
     * Creates the inspector.
     *
     * @param mobGoals {@code Bukkit.getMobGoals()}
     */
    public GoalInspector(MobGoals mobGoals) {
        this.mobGoals = Objects.requireNonNull(mobGoals, "mobGoals");
    }

    /**
     * Summarizes at most {@link #MAX_GOAL_LINES} goals of the mob. A goal counts as running when a running
     * goal has its key: Paper wraps vanilla goals in new objects on every call, and goals of the same class
     * share a key (DT-06).
     *
     * @param mob inspected mob
     * @return summaries with key, sorted types and running flag
     */
    public List<GoalSummary> summaries(Mob mob) {
        var runningKeys = new HashSet<String>();
        for (var goal : mobGoals.getRunningGoals(mob)) {
            runningKeys.add(goal.getKey().getNamespacedKey().toString());
        }
        var summaries = new ArrayList<GoalSummary>();
        for (var goal : mobGoals.getAllGoals(mob)) {
            if (summaries.size() == MAX_GOAL_LINES) {
                break;
            }
            var key = goal.getKey().getNamespacedKey().toString();
            var types = new TreeSet<String>();
            for (var type : goal.getTypes()) {
                types.add(type.name());
            }
            summaries.add(new GoalSummary(key, types, runningKeys.contains(key)));
        }
        return List.copyOf(summaries);
    }
}
