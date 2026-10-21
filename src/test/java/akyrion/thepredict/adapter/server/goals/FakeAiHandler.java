package akyrion.thepredict.adapter.server.goals;

import com.destroystokyo.paper.entity.ai.GoalType;
import io.lumine.mythic.api.volatilecode.handlers.VolatileAIHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MythicMobs AI handler double that behaves like {@code v26_2.VolatileAIHandlerImpl.addPathfinderGoals}
 * (javap): {@code clear} empties the fake goals, every other known line adds one goal, a goal name MythicMobs does
 * not register (here, any namespaced name such as {@code thepredict:micro}) is skipped silently, a line invalid for
 * the species is skipped with a logged error and the rest continue, and a line that fails is swallowed with a logged
 * error that stops the remaining lines; nothing is thrown.
 */
final class FakeAiHandler implements VolatileAIHandler {
    final FakeMobGoals goals;
    final List<List<String>> calls = new ArrayList<>();
    final List<String> loggedErrors = new ArrayList<>();
    String brokenGoal = "";
    final Set<String> invalidGoals = new HashSet<>();

    FakeAiHandler(FakeMobGoals goals) {
        this.goals = goals;
    }

    @Override
    public void addPathfinderGoals(LivingEntity entity, List<String> lines) {
        calls.add(List.copyOf(lines));
        for (var line : lines) {
            var parts = line.split(" ");
            var name = parts.length > 1 ? parts[1] : parts[0];
            var brace = name.indexOf('{');
            var bare = (brace < 0 ? name : name.substring(0, brace)).toLowerCase(Locale.ROOT);
            if (bare.equals(brokenGoal)) {
                loggedErrors.add("An error occurred while adding an AIGoalSelector, enable debugging for a stack trace.");
                return;
            }
            if (invalidGoals.contains(bare)) {
                loggedErrors.add("AI pathfinder " + bare + " is not valid for mob type");
                continue;
            }
            if (bare.indexOf(':') >= 0) {
                continue;
            }
            if (bare.equals("clear")) {
                goals.goals.clear();
                goals.priorities.clear();
            } else {
                goals.addGoal((Mob) entity, Integer.parseInt(parts[0]),
                        GoalFixtures.goal(GoalFixtures.vanillaKey(bare), GoalType.MOVE));
            }
        }
    }

    @Override
    public void addTargetGoals(LivingEntity entity, List<String> lines) {
        throw new UnsupportedOperationException();
    }
}
