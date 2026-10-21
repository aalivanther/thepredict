package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;

import java.util.UUID;

/** Actions that change nothing. Used until the outcome entry point is bound. */
final class QuietActions implements CombatActions {
    static final QuietActions INSTANCE = new QuietActions();

    private QuietActions() {
    }

    @Override
    public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
        return ExecutionOutcome.NOT_ELIGIBLE;
    }

    @Override
    public boolean ready(MobKey mob, SkillChoice choice) {
        return false;
    }

    @Override
    public void signalTactic(MobKey mob, TacticId tactic) {
    }

    @Override
    public void addThreat(MobKey mob, UUID participantId, double amount) {
    }
}
