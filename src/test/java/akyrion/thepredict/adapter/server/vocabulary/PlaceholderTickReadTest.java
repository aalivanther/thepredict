package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.adapter.server.vocabulary.PredictorPlaceholders.Field;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.caster;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.entity;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.placeholderMeta;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.view;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * No placeholder asks the server for its tick (SA-10, rule T3): a placeholder may be evaluated on a MythicMobs async
 * thread, so the telegraph reads the tick the coordinator published on the board.
 */
class PlaceholderTickReadTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000061");

    @Test
    void theTelegraphReadsThePublishedTickAndNoFieldAsksTheServer() {
        var board = new IntentBoard();
        var ticks = new int[1];
        var clock = ServerClock.of(() -> {
            ticks[0]++;
            return 40L;
        }, () -> 0L);
        var placeholders = new PredictorPlaceholders(board, clock);
        board.publishTick(40L);
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.HOLD, new TacticId("hold_line"), 100)
                .withPosture(Movement.HOLD, akyrion.thepredict.domain.CombatStance.PRESSING, false, (byte) 50,
                        TellAnswer.BLOCK.ordinal(), 50L));
        for (var field : Field.values()) {
            placeholders.resolve(field, placeholderMeta(caster(entity(ENTITY))));
        }
        assertEquals("10", placeholders.resolve(Field.TELEGRAPH, placeholderMeta(caster(entity(ENTITY)))));
        assertEquals(0, ticks[0], "no field asks the server tick");
    }
}
