package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.caster;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.clockAt;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.entity;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.line;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.view;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictorControlConditionTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final TacticId TACTIC = new TacticId("press_and_strike");

    @Test
    void jevAlternativeAndLocalControlWhileValid() {
        for (var level : new ControlLevel[] {ControlLevel.JEV, ControlLevel.ALTERNATIVE, ControlLevel.LOCAL}) {
            assertTrue(PredictorControlCondition.matches(view(level, Movement.APPROACH, TACTIC, 100), 99), level.name());
        }
    }

    @Test
    void baseExpiredAndAbsentViewsDoNotControl() {
        assertFalse(PredictorControlCondition.matches(view(ControlLevel.BASE, Movement.APPROACH, TACTIC, 100), 0));
        assertFalse(PredictorControlCondition.matches(view(ControlLevel.JEV, Movement.APPROACH, TACTIC, 100), 100));
        assertFalse(PredictorControlCondition.matches(IntentView.ABSENT, 0));
        assertFalse(PredictorControlCondition.matches(IntentView.base(2), 0));
    }

    @Test
    void expiryComparisonSurvivesClockOverflow() {
        var view = view(ControlLevel.JEV, Movement.HOLD, TACTIC, Long.MIN_VALUE + 10);
        assertTrue(PredictorControlCondition.matches(view, Long.MAX_VALUE - 10));
        assertFalse(PredictorControlCondition.matches(view, Long.MIN_VALUE + 10));
    }

    @Test
    void checkFollowsTheBoardForEntityAndCaster() {
        var board = new IntentBoard();
        var condition = new PredictorControlCondition(line(Map.of("ignored", "value")), board, clockAt(0));
        assertTrue(condition.isValid());
        assertFalse(condition.check(entity(ENTITY)));
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.LOCAL, Movement.HOLD, TACTIC, 10));
        assertTrue(condition.check(entity(ENTITY)));
        assertTrue(condition.check(caster(entity(ENTITY))));
        board.clearAll();
        assertFalse(condition.check(entity(ENTITY)));
    }

    @Test
    void clearAllTurnsEveryConditionFalseOnTheNextEvaluation() {
        var board = new IntentBoard();
        var clock = clockAt(0);
        var control = new PredictorControlCondition(board, clock);
        var move = new PredictorMoveCondition("hold", board, clock);
        var tactic = new PredictorTacticCondition("press_and_strike", board, clock);
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.HOLD, TACTIC, 10));
        assertTrue(control.test(ENTITY) && move.test(ENTITY) && tactic.test(ENTITY));
        board.clearAll();
        assertFalse(control.test(ENTITY));
        assertFalse(move.test(ENTITY));
        assertFalse(tactic.test(ENTITY));
    }

    @Test
    void evaluationAllocatesNothing() {
        var board = new IntentBoard();
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.LOCAL, Movement.HOLD, TACTIC, 10));
        var condition = new PredictorControlCondition(board, clockAt(0));
        var bytes = AllocationProbe.bytesAllocatedBy(() -> condition.test(ENTITY));
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
    }
}
