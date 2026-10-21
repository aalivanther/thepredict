package akyrion.thepredict;

import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.CombatActions;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Combat actions when the execution adapter failed to start: nothing is cast, ready, signalled or threatened (rule A6).
 */
final class NoCombatActions implements CombatActions {
    @Override
    public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
        return ExecutionOutcome.NOT_ELIGIBLE;
    }

    @Override
    public boolean ready(MobKey mob, SkillChoice choice) {
        return false;
    }

    @Override
    public OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
        return OptionalInt.empty();
    }

    @Override
    public void signalTactic(MobKey mob, TacticId tactic) {
    }

    @Override
    public void addThreat(MobKey mob, UUID participantId, double amount) {
    }
}
