package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A finished future is accepted in the decisions step; the layer casts on the tick that already holds the intent. */
class SameTickIntakeTest {
    @Test
    void anImmediateAnswerWaitsForTheNextTickLayer() {
        var fixture = prepared();
        fixture.client.immediate = request -> fixture.answered(fixture.client.requests.size() - 1,
                nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9)), ExecutionFixture.MODEL);
        fixture.runUntilRequest(1);
        var accepted = fixture.session(1).intent().orElseThrow();
        assertEquals(ControlLevel.JEV, accepted.level());
        assertFalse(accepted.skillConsumed());
        assertEquals(List.of(), fixture.actions.castTicks(1));
        fixture.run(1);
        assertEquals(List.of(accepted.startTick() + 1), fixture.actions.castTicks(1));
        assertTrue(fixture.session(1).intent().orElseThrow().skillConsumed());
    }

    @Test
    void aLateAnswerIsCastOnTheTickThatDrainsIt() {
        var fixture = prepared();
        fixture.runUntilRequest(1);
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
        fixture.answer(0, nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        var accepted = fixture.session(1).intent().orElseThrow();
        assertEquals(ControlLevel.JEV, accepted.level());
        assertEquals(List.of(accepted.startTick()), fixture.actions.castTicks(1));
        assertTrue(accepted.skillConsumed());
    }

    private static ExecutionFixture prepared() {
        var fixture = new ExecutionFixture();
        fixture.actions.cooldowns.setDefault(0);
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        return fixture;
    }
}
