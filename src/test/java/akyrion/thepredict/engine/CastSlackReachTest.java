package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PressureLevers;
import akyrion.thepredict.domain.ReactionMask;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.ZoneCastOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CastSlackReachTest {
    @Test
    void anOutOfReachChoiceCastsSlackTicksAfterReachReturns() {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var gate = new Reach();
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        control.useZone((session, choice, tick) -> ZoneCastOrder.Step.CAST);
        var choice = SkillChoice.untyped(new ChoiceId("strike"), "TPV_Strike", Set.of(), false, 0);
        var profile = profile(choice);
        var session = sessions.registry.open(EngineFixtures.id(1), profile);
        session.difficulty().levers(new PressureLevers(0, ReactionMask.ALL, 12, true, 0));
        var intent = new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD,
                Optional.of(choice.id()), 0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
        session.adoptIntent(intent);
        for (long tick = 0; tick <= 42; tick++) {
            gate.tick = tick;
            gate.reach = tick >= 30;
            control.attemptChoice(session, intent, choice, tick, false);
        }
        assertEquals(List.of(42L), gate.castTicks);
    }

    private static TacticalProfile profile(SkillChoice choice) {
        var hold = new TacticId("hold_ground");
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(hold, new Tactic(hold, Movement.HOLD, Optional.of(choice.id()), Optional.empty(), 50));
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(choice.id(), choice);
        return new TacticalProfile("slack_ai", "Guardian", "Defeat the target", tactics, choices, List.of(),
                MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                List.of(), new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    private static final class Reach implements CombatActions {
        final List<Long> castTicks = new ArrayList<>();
        long tick;
        boolean reach;

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            castTicks.add(tick);
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return true;
        }

        @Override
        public boolean cheaplyAdmitted(MobKey mob, SkillChoice choice, Movement movement) {
            return reach;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }
    }
}
