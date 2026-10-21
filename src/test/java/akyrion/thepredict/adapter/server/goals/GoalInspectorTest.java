package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.GoalSummary;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalInspectorTest {
    @Test
    void summariesMarkRunningGoalsByKeyAndStayBounded() {
        var goals = new FakeMobGoals();
        var mob = GoalFixtures.mob(UUID.randomUUID(), EntityType.ZOMBIE, null, () -> null, new Location(null, 0, 0, 0));
        goals.addGoal(mob, 8, GoalFixtures.goal(GoalFixtures.KEYS.hold(), GoalType.MOVE));
        goals.addGoal(mob, 12, GoalFixtures.goal(GoalFixtures.vanillaKey("look_at_target"), GoalType.LOOK, GoalType.MOVE));
        goals.running.add(GoalFixtures.goal(GoalFixtures.vanillaKey("look_at_target"), GoalType.LOOK));
        var summaries = new GoalInspector(goals).summaries(mob);
        assertEquals(new GoalSummary("thepredict:hold", Set.of("MOVE"), false), summaries.getFirst());
        assertEquals(new GoalSummary("minecraft:look_at_target", Set.of("LOOK", "MOVE"), true), summaries.getLast());
        for (var index = 0; index < GoalInspector.MAX_GOAL_LINES + 5; index++) {
            goals.addGoal(mob, 20 + index, GoalFixtures.goal(GoalFixtures.vanillaKey("extra_" + index), GoalType.MOVE));
        }
        assertEquals(GoalInspector.MAX_GOAL_LINES, new GoalInspector(goals).summaries(mob).size());
        assertTrue(new GoalInspector(goals).summaries(mob).stream().noneMatch(summary -> summary.key().isBlank()));
    }
}
