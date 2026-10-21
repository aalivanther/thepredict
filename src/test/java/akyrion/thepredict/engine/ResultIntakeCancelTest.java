package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release of an expired request cancels its future, and the client's completion then deposits a {@code NETWORK}
 * failure with the detail {@code cancelled} in the inbox (EFO-20). That entry is the echo of a result the intake
 * already handed over as {@code TIMEOUT}; it must not reach the acceptance as a second result.
 */
class ResultIntakeCancelTest {
    private final CoordinatorFixture fixture = new CoordinatorFixture();

    @Test
    void theCancellationEchoOfAnExpiredRequestNeverReachesTheAcceptance() {
        sendFirstRequest();
        var future = fixture.client.futures.getFirst();
        fixture.clock.nanos += fixture.settings.watchdogNanos();
        fixture.coordinator.tick();
        assertTrue(future.isCancelled());
        assertEquals(1, fixture.steps.accepted.size());
        assertEquals(FailureKind.TIMEOUT, kind(fixture.steps.accepted.getFirst()));
        fixture.clock.tick++;
        fixture.coordinator.tick();
        assertEquals(1, fixture.steps.accepted.size(), "the cancelled echo is not a second result");
        assertEquals(0, fixture.inbox.size(), "the echo is drained all the same");
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
    }

    @Test
    void aLateAnswerOfAnExpiredRequestStillReachesTheAcceptance() {
        sendFirstRequest();
        fixture.clock.nanos += fixture.settings.watchdogNanos();
        fixture.coordinator.tick();
        var released = fixture.steps.accepted.getFirst().request();
        fixture.inbox.offer(new InboxEntry.CompletedRequest(released,
                new DecisionResult.Failed(FailureKind.NETWORK, Optional.empty(), "the decision future failed"),
                fixture.clock.nanos));
        fixture.clock.tick++;
        fixture.coordinator.tick();
        assertEquals(2, fixture.steps.accepted.size(), "a real late result is still discarded by M10, not here");
    }

    private void sendFirstRequest() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        for (var tick = 0; tick < 4 && fixture.client.requests.isEmpty(); tick++) {
            fixture.run(1);
        }
        assertEquals(1, fixture.client.requests.size());
    }

    private static FailureKind kind(InboxEntry.CompletedRequest completed) {
        return ((DecisionResult.Failed) completed.result()).kind();
    }
}
