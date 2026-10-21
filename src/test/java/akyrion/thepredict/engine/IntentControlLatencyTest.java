package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceState;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.STRIKE;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "ready to cast" latency of M19.4 counts the ticks between the tick a choice is ready and its cast (EFO-09). A Jev
 * intent re-arms its choice in the ready step, so it casts on the tick its cooldown ends. A local intent re-arms a
 * consumed choice only in the reactive cycle of two ticks, so with an odd cooldown shorter than the local lifetime
 * (which re-plans the tactic armed) some casts start one tick late, and the buckets must show it.
 */
class IntentControlLatencyTest {
    private static final Function<QuestionSpec, Answer> PRESS_ON =
            nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9));
    private static final int ODD_COOLDOWN = 11;

    private static long casts(ExecutionFixture fixture) {
        return fixture.metrics.total(DecisionMetrics.ReadyCounter.READY_CASTS);
    }

    private static long bucketed(ExecutionFixture fixture) {
        var sum = 0L;
        for (var bucket = 0; bucket <= 3; bucket++) {
            sum += fixture.metrics.readyLatencyBucket(bucket);
        }
        return sum;
    }

    @Test
    void aLocalReArmOnTheReactiveCycleShowsInTheLatencyBuckets() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.captureCooldowns();
        fixture.actions.cooldowns.set(STRIKE, ODD_COOLDOWN);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.run(400);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(casts(fixture), bucketed(fixture), "every cast lands in one bucket");
        assertTrue(fixture.metrics.readyLatencyBucket(1) > 0,
                "with an odd cooldown the reactive cycle of two ticks re-arms a tick late: bucket 1 = "
                        + fixture.metrics.readyLatencyBucket(1) + " of " + casts(fixture));
        assertTrue(casts(fixture) >= 400 / (ODD_COOLDOWN + 2), "never later than one reactive cycle");
    }

    @Test
    void aJevReArmInTheReadyStepCastsOnTheTickTheCooldownEnds() {
        var fixture = new ExecutionFixture();
        fixture.actions.cooldowns.set(STRIKE, ODD_COOLDOWN);
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, PRESS_ON);
        fixture.runAnswering(400, PRESS_ON);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertTrue(casts(fixture) >= 10, "the strike fires about every cooldown: " + casts(fixture));
        assertEquals(casts(fixture), bucketed(fixture));
        assertEquals(casts(fixture), fixture.metrics.readyLatencyBucket(0),
                "a Jev choice is re-armed when it is due, never a tick late");
    }
}
