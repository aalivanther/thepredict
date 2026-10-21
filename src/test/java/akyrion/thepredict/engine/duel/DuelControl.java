package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.MobControl;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The goal ownership of the simulator: every set installs at once, unless the duelist is forced to level 4. With
 * {@link TickRules#LEGACY} the movement goal always runs, as in M18.7; with {@link TickRules#serverPorts} it runs only
 * while the goal the body selected wants to move at this distance, as a native goal of the server stops when its
 * condition fails, so {@code ProgressWatch} judges real progress (§7.2).
 */
final class DuelControl implements MobControl {
    private final DuelEngine engine;

    DuelControl(DuelEngine engine) {
        this.engine = engine;
    }

    @Override
    public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
        return engine.forcedBase.contains(key.entityId()) ? new InstallResult.Unsupported("forced to level 4")
                : new InstallResult.Installed(3);
    }

    @Override
    public MovementProgress progress(MobKey key) {
        var world = engine.world;
        if (world == null) {
            return new MovementProgress(true, true, true, OptionalDouble.empty());
        }
        var distance = world.distance();
        var running = !engine.rules.serverPorts() || engine.body(key.entityId()).goal.active(distance);
        return new MovementProgress(running, true, true, OptionalDouble.of(distance));
    }

    @Override
    public List<GoalSummary> describeGoals(MobKey key) {
        return List.of();
    }

    @Override
    public void release(MobKey key) {
    }
}
