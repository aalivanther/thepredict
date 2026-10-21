package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.DisplacementOrigin;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.HitShape;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PressureLevers;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.ReactionMask;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.ZoneCastOrder;
import akyrion.thepredict.domain.ZoneGuardKind;
import akyrion.thepredict.domain.ZoneMode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A disengage into a lethal far edge waits ten ticks. A leap does not (M24.5). */
class EscapeGuardTest {
    @Test
    void disengageIntoALethalFarEdgeWaitsTenTicksAndALeapStillCasts() {
        var kinds = new ArrayList<ZoneGuardKind>();
        var gate = new Gate();
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                new DecisionTap() {
                    @Override
                    public void zoneGuard(MobKey mob, ZoneGuardKind kind) {
                        kinds.add(kind);
                    }
                });
        control.useZone((session, choice, tick) -> ZoneCastOrder.Step.CAST);
        var back = choice("disengage", DisplacementKind.SELF_BACKWARD);
        var leap = choice("leap", DisplacementKind.SELF_FORWARD);
        var profile = profile(back, leap);
        var session = sessions.registry.open(EngineFixtures.id(1), profile);
        session.difficulty().levers(new PressureLevers(0, ReactionMask.ALL, 0, true, 0));
        session.zoneReading(ZoneMode.GUARD, PushDanger.UNKNOWN, Feasibility.UNKNOWN);
        session.tickSignals(TickSignals.pack(TickSignals.PRESENT, 0, 0, 0, 0, 40, 40, PushDanger.NONE.ordinal(),
                PushDanger.LETHAL.ordinal(), PushDanger.NONE.ordinal(), PushDanger.NONE.ordinal()));
        var intent = new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD,
                Optional.of(back.id()), 0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
        session.adoptIntent(intent);
        var held = control.attemptChoice(session, intent, back, 1L, false);
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, held);
        assertEquals(List.of(ZoneGuardKind.ESCAPE_BLOCKED), kinds);
        assertTrue(gate.castTicks.isEmpty());
        assertEquals(11L, session.nextReadyTick());
        var launched = control.attemptChoice(session, intent, leap, 1L, false);
        assertEquals(ExecutionOutcome.CAST, launched);
        assertEquals(List.of(1L), gate.castTicks);
    }

    private static SkillChoice choice(String name, DisplacementKind self) {
        var facts = new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false, false, false,
                OptionalInt.empty(), 0, 0, HitShape.UNKNOWN, DisplacementKind.NONE, self, DisplacementOrigin.READ);
        return new SkillChoice(new ChoiceId(name), ChoiceKind.MYTHIC_SKILL, Optional.of(name), Set.of(), false, 0,
                Optional.empty(), facts);
    }

    private static TacticalProfile profile(SkillChoice back, SkillChoice leap) {
        var hold = new TacticId("hold_ground");
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(hold, new Tactic(hold, Movement.HOLD, Optional.of(back.id()), Optional.empty(), 50));
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(back.id(), back);
        choices.put(leap.id(), leap);
        return new TacticalProfile("escape_ai", "Guardian", "Defeat the target", tactics, choices, List.of(),
                MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                List.of(), new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    private static final class Gate implements CombatActions {
        final List<Long> castTicks = new ArrayList<>();

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            castTicks.add(1L);
            return ExecutionOutcome.CAST;
        }

        @Override public boolean ready(MobKey mob, SkillChoice choice) { return true; }
        @Override public boolean cheaplyAdmitted(MobKey mob, SkillChoice choice, Movement movement) { return true; }
        @Override public void signalTactic(MobKey mob, TacticId tactic) { }
        @Override public void addThreat(MobKey mob, UUID participantId, double amount) { }
    }
}
