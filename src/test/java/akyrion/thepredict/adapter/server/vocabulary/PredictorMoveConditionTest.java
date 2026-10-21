package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.caster;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.clockAt;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.entity;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.line;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictorMoveConditionTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static IntentView view(ControlLevel level, Movement movement, long expiresAtNanos) {
        return VocabularyFixtures.view(level, movement, new TacticId("debug_" + movement.name().toLowerCase(Locale.ROOT)), expiresAtNanos);
    }

    @Test
    void matchesOnlyTheRequestedMovementWhileControlling() {
        assertTrue(PredictorMoveCondition.matches(view(ControlLevel.LOCAL, Movement.RETREAT, 100), Movement.RETREAT, 0));
        assertFalse(PredictorMoveCondition.matches(view(ControlLevel.LOCAL, Movement.RETREAT, 100), Movement.APPROACH, 0));
    }

    @Test
    void everyControllingLevelMatchesAndBaseDoesNot() {
        assertTrue(PredictorMoveCondition.matches(view(ControlLevel.JEV, Movement.HOLD, 100), Movement.HOLD, 0));
        assertTrue(PredictorMoveCondition.matches(view(ControlLevel.ALTERNATIVE, Movement.HOLD, 100), Movement.HOLD, 0));
        assertFalse(PredictorMoveCondition.matches(view(ControlLevel.BASE, Movement.HOLD, 100), Movement.HOLD, 0));
    }

    @Test
    void expiredAndAbsentViewsDoNotMatch() {
        assertTrue(PredictorMoveCondition.matches(view(ControlLevel.LOCAL, Movement.HOLD, 100), Movement.HOLD, 99));
        assertFalse(PredictorMoveCondition.matches(view(ControlLevel.LOCAL, Movement.HOLD, 100), Movement.HOLD, 100));
        assertFalse(PredictorMoveCondition.matches(IntentView.ABSENT, Movement.NONE, 0));
    }

    @Test
    void checkReadsTheBoardForTheEvaluatedEntityAndCaster() {
        var board = new IntentBoard();
        var condition = new PredictorMoveCondition("keep_distance", board, clockAt(0));
        var entity = entity(ENTITY);
        assertFalse(condition.check(entity));
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.LOCAL, Movement.KEEP_DISTANCE, 10));
        assertTrue(condition.check(entity));
        assertTrue(condition.check(caster(entity)));
        board.clear(ENTITY);
        assertFalse(condition.check(entity));
    }

    @Test
    void aNewGenerationAtBaseLevelStopsTheOldIntent() {
        var board = new IntentBoard();
        var condition = new PredictorMoveCondition("hold", board, clockAt(0));
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.LOCAL, Movement.HOLD, 10));
        assertTrue(condition.test(ENTITY));
        board.publish(new MobKey(ENTITY, 1), IntentView.base(1));
        assertFalse(condition.test(ENTITY));
    }

    @Test
    void missingCasterEntityOrUuidIsFalse() {
        var condition = new PredictorMoveCondition("hold", new IntentBoard(), clockAt(0));
        assertFalse(condition.check(caster(null)));
        assertFalse(condition.check(entity(null)));
        assertFalse(condition.test(null));
    }

    @Test
    void argumentIsReadFromEitherKeyWithoutCase() {
        var board = new IntentBoard();
        var clock = clockAt(0);
        assertEquals(Movement.HOLD, new PredictorMoveCondition(line(Map.of("m", "HOLD")), board, clock).movement());
        assertEquals(Movement.KEEP_DISTANCE,
                new PredictorMoveCondition(line(Map.of("movement", " Keep_Distance ")), board, clock).movement());
        assertTrue(new PredictorMoveCondition(line(Map.of("m", "flank")), board, clock).isValid());
    }

    @Test
    void invalidArgumentMakesTheConditionAlwaysFalse() {
        var board = new IntentBoard();
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.LOCAL, Movement.NONE, 10));
        var condition = new PredictorMoveCondition("sideways", board, clockAt(0));
        assertFalse(condition.isValid());
        assertFalse(condition.check(entity(ENTITY)));
        assertTrue(condition.problem().orElseThrow().contains("'sideways'"));
    }

    @Test
    void missingArgumentIsInvalidAndNotEchoed() {
        var condition = new PredictorMoveCondition(line(Map.of()), new IntentBoard(), clockAt(0));
        assertFalse(condition.isValid());
        assertTrue(condition.problem().orElseThrow().contains("no value"));
        var symbols = new PredictorMoveCondition("sk-live/SECRET", new IntentBoard(), clockAt(0));
        assertFalse(symbols.problem().orElseThrow().contains("SECRET"));
    }

    @Test
    void evaluationAllocatesNothing() {
        var board = new IntentBoard();
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.RETREAT, 10));
        var condition = new PredictorMoveCondition("retreat", board, clockAt(0));
        var bytes = AllocationProbe.bytesAllocatedBy(() -> condition.test(ENTITY));
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
    }
}
