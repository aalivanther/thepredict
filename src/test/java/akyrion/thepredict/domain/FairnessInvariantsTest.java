package akyrion.thepredict.domain;

import akyrion.thepredict.adapter.jev.FairQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** A rung changes levers and does not change the bytes sent to Jev (M22.7). */
class FairnessInvariantsTest {
    @Test
    void rungZeroAndRungEightShareTheQuestionBody() {
        var profile = DomainFixtures.guardian();
        var options = List.of(new TacticId("press_attack"), new TacticId("hold_ground"));
        var body = FairQuestion.json(Situation.NOW, options, profile);
        assertEquals(body, FairQuestion.json(Situation.NOW, options, profile));
        assertFalse(body.contains("reactionDelay"));
        assertFalse(body.contains("damagingCastSlack"));
        var low = PressureLadder.levers(0, 0, 5, true);
        var high = PressureLadder.levers(8, 0, 5, true);
        assertNotEquals(low.reactionDelayTicks(), high.reactionDelayTicks());
        assertNotEquals(low.reactionMask(), high.reactionMask());
    }
}
