package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.TellAnswer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hybrid telegraph publishes the board and does not clear a fury that is already on (M23.8). */
class TellBoardTest {
    @Test
    void hybridTelegraphPublishesWindupAndKeepsEnrage() {
        long start = 400L;
        var choice = slam(TellAnswer.BACK_OFF, 10, 12);
        var session = session(choice, playersOff(), Optional.empty());
        session.playerTarget(true);
        session.recordTarget(UUID.randomUUID(), 3);
        session.stanceState(StanceState.withEnraged(session.stanceState(), true));
        var board = new IntentBoard();
        board.publish(session.key(), IntentView.base(0));
        session.useBoard(board);
        var spy = new Spy();
        assertFalse(TellOpening.hold(session, choice, Movement.APPROACH, start, spy, new long[5]));
        assertEquals(ActionStance.WINDUP, session.actionStance());
        assertTrue(StanceState.enraged(session.stanceState()));
        assertEquals(List.of("windup"), spy.variables);
        assertTrue(spy.natives.isEmpty());
        var view = board.current(session.key().entityId());
        assertEquals(CombatStance.WINDUP, view.stance());
        assertEquals(Optional.of(TellAnswer.BACK_OFF), view.answer());
        assertEquals(start + 10, view.impactAtTick());
        assertEquals(Movement.HOLD, view.movement());
        assertTrue(view.enraged());
    }

    @Test
    void adoptingAnIntentKeepsTheOpenTellAndPublishesPoiseWhenItChanges() {
        long start = 400L;
        var choice = slam(TellAnswer.BLOCK, 10, 12);
        var session = session(choice, playersOff(), Optional.empty());
        session.playerTarget(true);
        session.recordTarget(UUID.randomUUID(), 3);
        var harness = new EngineFixtures.Sessions();
        harness.board.publish(session.key(), IntentView.base(0));
        session.useBoard(harness.board);
        assertFalse(TellOpening.hold(session, choice, Movement.APPROACH, start, QuietActions.INSTANCE, new long[5]));
        var metrics = new DecisionMetrics(() -> 0L);
        var control = new IntentControl(harness.board, QuietActions.INSTANCE, metrics,
                new ExecutionLog(line -> {
                }, false), new OutcomeEvents(harness.registry, harness.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        control.apply(session, EngineFixtures.intent(session.key()), start);
        var adopted = harness.board.current(session.key().entityId());
        assertEquals(ControlLevel.JEV, adopted.level());
        assertEquals(CombatStance.WINDUP, adopted.stance());
        assertEquals(Optional.of(TellAnswer.BLOCK), adopted.answer());
        assertEquals(start + 10, adopted.impactAtTick());
        session.poiseShown(15);
        session.stanceState(StanceState.withEnraged(session.stanceState(), true));
        control.showIntent(session, 0L);
        var shown = harness.board.current(session.key().entityId());
        assertEquals(15, shown.poisePercent());
        assertTrue(shown.enraged());
        assertEquals(CombatStance.WINDUP, shown.stance());
        assertEquals(new TacticId("hold_ground"), shown.tactic());
    }

    @Test
    void poisePublishesWhenThePercentMovesByFivePoints() {
        var choice = slam(TellAnswer.BACK_OFF, 10, 12);
        var session = session(choice, TelegraphSettings.DEFAULT, Optional.of(PoiseSettings.defaults()));
        var board = new IntentBoard();
        board.publish(session.key(), IntentView.base(0));
        session.useBoard(board);
        var attacker = UUID.randomUUID();
        var step = new long[5];
        PoiseWatch.onDamage(session, attacker, 0.03, 10L, true, QuietActions.INSTANCE, step);
        assertEquals(20, board.current(session.key().entityId()).poisePercent());
        PoiseWatch.onDamage(session, attacker, 0.003, 11L, true, QuietActions.INSTANCE, step);
        assertEquals(20, board.current(session.key().entityId()).poisePercent());
        PoiseWatch.onDamage(session, attacker, 0.015, 12L, true, QuietActions.INSTANCE, step);
        assertEquals(32, board.current(session.key().entityId()).poisePercent());
        assertEquals(ActionStance.NONE, session.actionStance());
    }

    private static MobSession session(SkillChoice choice, TelegraphSettings telegraph, Optional<PoiseSettings> poise) {
        var base = EngineFixtures.profile("Guardian");
        return new MobSession(new MobKey(UUID.randomUUID(), 0), new TacticalProfile(base.id(), base.mythicMobId(),
                base.objective(), base.tactics(), Map.of(choice.id(), choice), base.contexts(), base.movement(),
                base.health(), base.threat(), base.baseAi(), base.timing(), base.participants(), base.difficulty(),
                base.stances(), telegraph, poise));
    }

    private static SkillChoice slam(TellAnswer answer, int windUp, int recovery) {
        var id = new ChoiceId("slam");
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), windUp, CooldownClass.SHORT, DamageTier.HIGH, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "Slam", Set.of(), true, 0, purpose).withTelegraph(new ChoiceTelegraph(
                Optional.empty(), Optional.of(answer), OptionalInt.of(recovery)));
    }

    private static TelegraphSettings playersOff() {
        var defaults = TelegraphSettings.DEFAULT;
        return new TelegraphSettings(false, defaults.againstEntities(), defaults.light(), defaults.medium(),
                defaults.heavy(), defaults.severe(), defaults.blockTicks(), defaults.dodgeTicks(),
                defaults.backOffTicks(), defaults.breakSightTicks(), defaults.interruptTicks(),
                defaults.deliveryMarginTicks(), defaults.maxReleasesPerTick(), defaults.whiffRecoveryTicks(),
                defaults.whiffCooldownTicks(), defaults.resultWindowTicks(), defaults.commitmentPercent(),
                defaults.entropyCommitmentPercent(), defaults.shortWindUp(), defaults.defaultRecoveryTicks());
    }

    private static final class Spy implements CombatActions {
        private final List<String> variables = new ArrayList<>();
        private final List<String> natives = new ArrayList<>();

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return true;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }

        @Override
        public void writeStanceVariable(MobKey mob, String stance) {
            variables.add(stance);
        }

        @Override
        public void writeNativeStance(MobKey mob, String stance) {
            natives.add(stance);
        }
    }
}
