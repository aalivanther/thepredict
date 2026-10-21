package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TacticSignalsTest {
    @Test
    void shortestTacticGetsThePrefixInUpperCase() {
        assertEquals("PREDICTOR_ABC", TacticSignals.nameOf(new TacticId("abc")));
    }

    @Test
    void longestTacticKeepsDigitsAndUnderscores() {
        var tactic = new TacticId("retreat_and_defend_2" + "x".repeat(20));
        var name = TacticSignals.nameOf(tactic);
        assertEquals("PREDICTOR_RETREAT_AND_DEFEND_2" + "X".repeat(20), name);
        assertEquals(TacticSignals.PREFIX.length() + 40, name.length());
    }

    @Test
    void exampleFromTheAuthorGuide() {
        assertEquals("PREDICTOR_RETREAT_AND_DEFEND", TacticSignals.nameOf(new TacticId("retreat_and_defend")));
    }

    @Test
    void missingTacticIsRejected() {
        assertThrows(NullPointerException.class, () -> TacticSignals.nameOf(null));
    }
}
