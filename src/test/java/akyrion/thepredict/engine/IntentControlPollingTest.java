package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.ServiceState;
import org.junit.jupiter.api.Test;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumed choice that cannot be re-armed yet does not wake the session every tick (EFO-11): after a rejection of
 * MythicMobs the choice waits one local intent lifetime before its re-arm, so the ready step has nothing to try until
 * then. The cast itself comes on the same tick as before.
 */
class IntentControlPollingTest {
    private static long checks(ExecutionFixture fixture) {
        return fixture.metrics.total(DecisionMetrics.ReadyCounter.READY_CHECKS);
    }

    private static long retryTicks(ExecutionFixture fixture) {
        return FallbackStep.localIntentTicks(fixture.session(1).profile().timing());
    }

    @Test
    void aRejectedJevChoiceIsNotPolledUntilItsRetry() {
        var fixture = new ExecutionFixture();
        fixture.actions.cooldowns.setDefault(0);
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.actions.script(1, ExecutionOutcome.REJECTED_BY_MYTHICMOBS);
        fixture.runUntilRequest(1);
        fixture.answer(0, nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        var session = fixture.session(1);
        assertEquals(ControlLevel.JEV, session.intent().orElseThrow().level());
        assertTrue(session.intent().orElseThrow().skillConsumed(), "MythicMobs rejected the first cast");
        var rejected = session.choiceConsumedTick().orElseThrow();
        var before = checks(fixture);
        fixture.run((int) retryTicks(fixture) - 2);
        assertTrue(checks(fixture) - before <= 2, "ready checks before the retry: " + (checks(fixture) - before));
        assertTrue(fixture.actions.castTicks(1).isEmpty());
        fixture.run(4);
        assertTrue(!fixture.actions.castTicks(1).isEmpty(), "the choice fires again once its retry has passed");
        assertEquals(rejected + retryTicks(fixture), fixture.actions.castTicks(1).getFirst(),
                "on the first tick the re-arm allows, as before");
    }

    @Test
    void aRejectedLocalChoiceIsNotPolledUntilItsRetry() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.actions.cooldowns.setDefault(0);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.actions.script(1, ExecutionOutcome.REJECTED_BY_MYTHICMOBS);
        var session = fixture.session(1);
        for (var tick = 0; tick < 100 && session.choiceConsumedTick().isEmpty(); tick++) {
            fixture.run(1);
        }
        assertEquals(ControlLevel.LOCAL, session.intent().orElseThrow().level());
        assertTrue(session.intent().orElseThrow().skillConsumed(), "MythicMobs rejected the first cast");
        var before = checks(fixture);
        fixture.run((int) retryTicks(fixture) - 2);
        assertTrue(checks(fixture) - before <= 2, "ready checks before the retry: " + (checks(fixture) - before));
        assertTrue(fixture.actions.castTicks(1).isEmpty());
    }
}
