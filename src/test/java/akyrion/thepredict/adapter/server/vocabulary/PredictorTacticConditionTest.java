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

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.clockAt;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.entity;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.line;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.view;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictorTacticConditionTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final TacticId RETREAT = new TacticId("retreat_and_defend");

    @Test
    void matchesOnlyTheSameTacticWhileControlling() {
        assertTrue(PredictorTacticCondition.matches(view(ControlLevel.JEV, Movement.RETREAT, RETREAT, 100), RETREAT, 0));
        assertTrue(PredictorTacticCondition.matches(view(ControlLevel.ALTERNATIVE, Movement.RETREAT, RETREAT, 100),
                RETREAT, 0));
        assertTrue(PredictorTacticCondition.matches(view(ControlLevel.LOCAL, Movement.RETREAT, RETREAT, 100), RETREAT, 0));
        assertFalse(PredictorTacticCondition.matches(view(ControlLevel.JEV, Movement.RETREAT, new TacticId("hold_line"), 100),
                RETREAT, 0));
    }

    @Test
    void baseExpiredAndAbsentViewsDoNotMatch() {
        assertFalse(PredictorTacticCondition.matches(view(ControlLevel.BASE, Movement.RETREAT, RETREAT, 100), RETREAT, 0));
        assertFalse(PredictorTacticCondition.matches(view(ControlLevel.JEV, Movement.RETREAT, RETREAT, 100), RETREAT, 100));
        assertFalse(PredictorTacticCondition.matches(IntentView.ABSENT, TacticId.NONE, 0));
    }

    @Test
    void checkReadsTheBoard() {
        var board = new IntentBoard();
        var condition = new PredictorTacticCondition(line(Map.of("t", "retreat_and_defend")), board, clockAt(0));
        assertEquals(RETREAT, condition.tactic());
        assertFalse(condition.check(entity(ENTITY)));
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.RETREAT, RETREAT, 10));
        assertTrue(condition.check(entity(ENTITY)));
    }

    @Test
    void tacticKeyAliasAndBoundaryLengthsAreAccepted() {
        var board = new IntentBoard();
        var clock = clockAt(0);
        assertEquals(new TacticId("abc"), new PredictorTacticCondition(line(Map.of("tactic", "abc")), board, clock).tactic());
        var longest = "a" + "b".repeat(39);
        assertTrue(new PredictorTacticCondition(longest, board, clock).isValid());
        assertFalse(new PredictorTacticCondition(longest + "c", board, clock).isValid());
        assertFalse(new PredictorTacticCondition("ab", board, clock).isValid());
        assertTrue(new PredictorTacticCondition("debug_hold", board, clock).isValid());
    }

    @Test
    void reservedNoneTacticIsInvalidBecauseNoControllingViewCarriesIt() {
        var board = new IntentBoard();
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.HOLD, TacticId.NONE, 10));
        var condition = new PredictorTacticCondition(line(Map.of("t", "none")), board, clockAt(0));
        assertFalse(condition.isValid());
        assertFalse(condition.check(entity(ENTITY)));
        assertTrue(condition.problem().orElseThrow().contains("reserved"));
    }

    @Test
    void upperCaseTacticIsInvalidAndTheWarningSuggestsTheLowerCaseId() {
        var condition = new PredictorTacticCondition("RETREAT_AND_DEFEND", new IntentBoard(), clockAt(0));
        assertFalse(condition.isValid());
        assertEquals(TacticId.NONE, condition.tactic());
        assertTrue(condition.problem().orElseThrow().contains("for example 'retreat_and_defend'"));
    }

    @Test
    void missingOrSymbolArgumentIsInvalidAndAlwaysFalse() {
        var board = new IntentBoard();
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.RETREAT, RETREAT, 10));
        var missing = new PredictorTacticCondition(line(Map.of()), board, clockAt(0));
        assertFalse(missing.check(entity(ENTITY)));
        assertTrue(missing.problem().orElseThrow().contains("no value"));
        var symbols = new PredictorTacticCondition("retreat-and-defend!", board, clockAt(0));
        assertFalse(symbols.check(entity(ENTITY)));
        assertFalse(symbols.problem().orElseThrow().contains("retreat-and-defend"));
    }

    @Test
    void evaluationAllocatesNothing() {
        var board = new IntentBoard();
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.RETREAT, RETREAT, 10));
        var condition = new PredictorTacticCondition("retreat_and_defend", board, clockAt(0));
        var bytes = AllocationProbe.bytesAllocatedBy(() -> condition.test(ENTITY));
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
    }
}
