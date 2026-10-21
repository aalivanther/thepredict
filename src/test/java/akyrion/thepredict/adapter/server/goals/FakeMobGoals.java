package akyrion.thepredict.adapter.server.goals;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * In-memory {@link MobGoals} for one mob: goals in insertion order and a set of running goals; can fail when
 * ThePredict adds a Paper goal.
 */
final class FakeMobGoals implements MobGoals {
    final List<Goal<Mob>> goals = new ArrayList<>();
    final List<Goal<Mob>> running = new ArrayList<>();
    final List<Integer> priorities = new ArrayList<>();
    RuntimeException addFailure;

    long count(GoalKey<Mob> key) {
        return goals.stream().filter(goal -> goal.getKey().equals(key)).count();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Mob> void addGoal(T mob, int priority, Goal<T> goal) {
        if (addFailure != null && goal.getKey().getNamespacedKey().getNamespace().equals("thepredict")) {
            throw addFailure;
        }
        goals.add((Goal<Mob>) goal);
        priorities.add(priority);
    }

    @Override
    public <T extends Mob> void removeGoal(T mob, Goal<T> goal) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Mob> void removeAllGoals(T mob) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Mob> void removeAllGoals(T mob, GoalType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Mob> void removeGoal(T mob, GoalKey<T> key) {
        for (var index = goals.size() - 1; index >= 0; index--) {
            if (goals.get(index).getKey().equals(key)) {
                goals.remove(index);
                priorities.remove(index);
            }
        }
    }

    @Override
    public <T extends Mob> boolean hasGoal(T mob, GoalKey<T> key) {
        return goals.stream().anyMatch(goal -> goal.getKey().equals(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Mob> Goal<T> getGoal(T mob, GoalKey<T> key) {
        return (Goal<T>) goals.stream().filter(goal -> goal.getKey().equals(key)).findFirst().orElse(null);
    }

    @Override
    public <T extends Mob> Collection<Goal<T>> getGoals(T mob, GoalKey<T> key) {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Mob> Collection<Goal<T>> getAllGoals(T mob) {
        return (Collection<Goal<T>>) (Collection<?>) List.copyOf(goals);
    }

    @Override
    public <T extends Mob> Collection<Goal<T>> getAllGoals(T mob, GoalType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Mob> Collection<Goal<T>> getAllGoalsWithout(T mob, GoalType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Mob> Collection<Goal<T>> getRunningGoals(T mob) {
        return (Collection<Goal<T>>) (Collection<?>) List.copyOf(running);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Mob> Collection<Goal<T>> getRunningGoals(T mob, GoalType type) {
        return (Collection<Goal<T>>) (Collection<?>) running.stream().filter(goal -> goal.getTypes().contains(type))
                .toList();
    }

    @Override
    public <T extends Mob> Collection<Goal<T>> getRunningGoalsWithout(T mob, GoalType type) {
        throw new UnsupportedOperationException();
    }
}
