package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.CandidateIntent;
import akyrion.thepredict.domain.CandidateSet;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PatternGroup;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ObserveLearnedParityTest {
    @Test
    void observeLeavesTheKeyAndTheQuestionOrder() {
        assertEquals(0, ContextKey.patternCode(PatternGroup.RHYTHM, false));
        var key = ContextKey.of(0, HealthBand3.HIGH, HealthBand3.MID, false, ZoneBand.OPEN, GroupBand.SOLO,
                ContextKey.patternCode(PatternGroup.RHYTHM, false));
        assertEquals("v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE", key.text());
        var press = new CandidateIntent(new TacticId("press"), Movement.APPROACH, Optional.empty(), 100, 0);
        var keep = new CandidateIntent(new TacticId("keep"), Movement.HOLD, Optional.empty(), 900, 0);
        var order = List.of(press.tactic(), keep.tactic());
        var set = CandidateSet.of(1L, 0, List.of(press, keep), Map.of(), order);
        assertEquals(order, set.questionOrder());
        assertNotEquals(order.getFirst(), set.candidates().getFirst().tactic());
    }
}
