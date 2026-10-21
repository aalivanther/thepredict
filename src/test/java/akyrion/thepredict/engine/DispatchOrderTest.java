package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M20.7 same-tick seam: a future that is already complete is accepted on the server thread after the body was
 * recorded, so a recording hears the body before the result and the acceptance runs outside any callback.
 */
class DispatchOrderTest {
    @Test
    void theBodyIsRecordedBeforeAnImmediateResultIsAccepted() {
        var fixture = new ExecutionFixture();
        fixture.actions.cooldowns.setDefault(0);
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.client.immediate = request -> fixture.answered(fixture.client.requests.size() - 1,
                nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9)), ExecutionFixture.MODEL);
        var calls = new ArrayList<String>();
        fixture.tap.attach(new DecisionTap() {
            @Override
            public boolean recording() {
                return true;
            }

            @Override
            public void requestBody(EncounterId encounter, long sequence, String body) {
                calls.add("body " + sequence);
            }

            @Override
            public void resultReceived(EncounterId encounter, long sequence, DecisionResult result,
                                       long roundTripNanos) {
                calls.add("result " + sequence);
            }
        }, () -> calls.add("detached"));
        fixture.runUntilRequest(1);
        assertEquals(List.of("body 1", "result 1"), calls);
    }
}
