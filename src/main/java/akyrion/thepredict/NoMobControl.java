package akyrion.thepredict;

import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.MobControl;

import java.util.List;
import java.util.OptionalDouble;

/** Goal ownership when the goal tools failed to start: nothing is installed and every mob keeps its AI (rule A6). */
final class NoMobControl implements MobControl {
    @Override
    public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
        return new InstallResult.Unsupported("goal ownership is unavailable");
    }

    @Override
    public MovementProgress progress(MobKey key) {
        return new MovementProgress(false, false, false, OptionalDouble.empty());
    }

    @Override
    public List<GoalSummary> describeGoals(MobKey key) {
        return List.of();
    }

    @Override
    public void release(MobKey key) {
    }
}
