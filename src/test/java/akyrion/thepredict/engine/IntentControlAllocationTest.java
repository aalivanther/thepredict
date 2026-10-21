package akyrion.thepredict.engine;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ready step of a mob whose skill is still cooling down allocates nothing (EFO-18, C12): the check that answers
 * {@code NOT_ELIGIBLE} and the recalibration of the reactive cycle run on every visit. The port double returns shared
 * values, so only the engine is measured. {@code ANY_READY} also walks the candidates of {@code ReadyCasts}, which is
 * measured by its own tests.
 */
class IntentControlAllocationTest {
    private static final OptionalInt ONE_TICK = OptionalInt.of(1);

    /** Combat actions whose skill is always cooling down, without allocating. */
    private static final class Cooling implements CombatActions {
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
            return ONE_TICK;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID playerId, double amount) {
        }
    }

    private final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    private final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
    private final IntentControl control = new IntentControl(sessions.board, new Cooling(), metrics,
            new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
            DecisionTap.NONE);

    private MobSession cooling(int mob, CastMode mode) {
        var session = sessions.registry.open(EngineFixtures.id(mob),
                ExecutionFixture.profile(ExecutionFixture.timing(1).withCasting(mode, 0), 0));
        session.recordTarget(EngineFixtures.player(1), 3.0);
        session.adoptIntent(new TacticalIntent(session.key(), ExecutionFixture.PRESS, Movement.APPROACH,
                Optional.of(ExecutionFixture.STRIKE), 0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE,
                false));
        return session;
    }

    @Test
    void aReadyCheckThatFindsTheSkillCoolingDownAllocatesNothing() {
        var session = cooling(1, CastMode.INTENT_ONLY);
        var tick = new long[] {100};
        var bytes = AllocationProbe.bytesAllocatedBy(() -> {
            session.scheduleReady(tick[0]);
            control.castIfReady(session, tick[0], 0L);
            tick[0]++;
        });
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
        assertTrue(metrics.total(DecisionMetrics.ReadyCounter.READY_CHECKS) > AllocationProbe.MEASURED_CALLS);
    }

    @Test
    void theRecalibrationOfTheReactiveCycleAllocatesNothing() {
        var session = cooling(2, CastMode.INTENT_ONLY);
        var bytes = AllocationProbe.bytesAllocatedBy(() -> control.recalibrate(session, 100L));
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
    }
}
