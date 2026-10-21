package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.MobKey;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PZ-26: a forced rung applies at once and then reads the world only on the adjust tick (M22: never per tick). */
class SessionDifficultyForcedTest {
    @Test
    void aForcedRungReadsTheWorldOnceAndThenOnlyOnTheAdjustTicks() {
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        session.recordTarget(EngineFixtures.player(1), 3.0);
        var reader = new QuietReader(Map.of(), true);
        var board = new IntentBoard();
        board.publish(session.key(), akyrion.thepredict.domain.IntentView.base(0));
        session.difficulty().touch(session, 0L, reader, DecisionTap.NONE, board);
        session.difficulty().forceRung(3);
        session.difficulty().touch(session, 1L, reader, DecisionTap.NONE, board);
        assertEquals(3, session.difficulty().rung(), "the forced rung applies on the tick it is set");
        reader.resetWorldReads();
        var ticks = 400;
        for (var tick = 2L; tick < 2L + ticks; tick++) {
            session.difficulty().touch(session, tick, reader, DecisionTap.NONE, board);
        }
        var interval = DifficultyGlobals.DEFAULT.adjustIntervalTicks();
        var reads = reader.worldReads();
        assertTrue(reads <= ticks / interval + 1, reads + " reads in " + ticks + " ticks");
        assertEquals(3, session.difficulty().rung(), "the fast loop does not move a forced rung");
    }
}
