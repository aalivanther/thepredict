package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static akyrion.thepredict.domain.DomainFixtures.answer;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnswerTest {
    @Test
    void rankedOrdersByProbabilityThenAlphabetically() {
        var answer = answer(0.9, "keep_range", 0.3, "press_attack", 0.3, "defend_self", 0.4);
        assertEquals(List.of(tactic("defend_self"), tactic("keep_range"), tactic("press_attack")), answer.ranked());
        assertEquals(tactic("defend_self"), answer.best());
    }

    @Test
    void tiesAreBrokenStablyByTacticName() {
        var answer = answer(0.9, "zeta_move", 0.5, "alpha_move", 0.5);
        assertEquals(tactic("alpha_move"), answer.best());
        assertEquals(tactic("alpha_move"), answer(0.9, "alpha_move", 0.5, "zeta_move", 0.5).best());
    }

    @Test
    void probabilityOfAMissingTacticIsZero() {
        assertEquals(0.0, answer(0.9, "hold_ground", 1.0).probabilityOf(tactic("keep_range")));
        assertEquals(1.0, answer(0.9, "hold_ground", 1.0).probabilityOf(tactic("hold_ground")));
    }

    @Test
    void nanAndInfiniteValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> answer(0.9, "hold_ground", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> answer(0.9, "hold_ground", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> answer(Double.NaN, "hold_ground", 1.0));
        assertThrows(IllegalArgumentException.class, () -> answer(1.5, "hold_ground", 1.0));
    }

    @Test
    void sumWithinTheToleranceIsAccepted() {
        assertEquals(0.98, answer(0.9, "hold_ground", 0.5, "keep_range", 0.48).probabilities().values()
                .stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        assertEquals(tactic("hold_ground"), answer(0.9, "hold_ground", 0.52, "keep_range", 0.5).best());
    }

    @Test
    void sumOutsideTheToleranceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> answer(0.9, "hold_ground", 0.5, "keep_range", 0.47));
        assertThrows(IllegalArgumentException.class, () -> answer(0.9, "hold_ground", 0.5, "keep_range", 0.45));
        assertThrows(IllegalArgumentException.class, () -> answer(0.9, "hold_ground", 0.6, "keep_range", 0.43));
    }

    @Test
    void emptyProbabilitiesAreRejectedAndInputIsCopied() {
        assertThrows(IllegalArgumentException.class, () -> new Answer(Map.of(), 0.5));
        var source = new HashMap<TacticId, Double>(Map.of(tactic("hold_ground"), 1.0));
        var answer = new Answer(source, 0.5);
        source.put(tactic("keep_range"), 0.0);
        assertEquals(1, answer.probabilities().size());
    }
}
