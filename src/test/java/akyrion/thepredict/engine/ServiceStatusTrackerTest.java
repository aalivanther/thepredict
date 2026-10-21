package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceStatusTrackerTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long CHECK_TIMEOUT = 2 * SECOND;

    private final List<String> log = new ArrayList<>();
    private double random = 0.5;
    private final ServiceStatusTracker tracker = tracker(ServiceState.CHECKING);

    private ServiceStatusTracker tracker(ServiceState initial) {
        return new ServiceStatusTracker(new ServiceCheck(initial, "start"), CHECK_TIMEOUT, () -> random, log::add);
    }

    private static DecisionResult.Failed failed(FailureKind kind) {
        return new DecisionResult.Failed(kind, Optional.empty(), "HTTP detail");
    }

    private static DecisionResult.Answered answered() {
        return new DecisionResult.Answered(Map.of(), "jev-1.13.0", 400);
    }

    private void checkReady(long now) {
        tracker.onCheck(tracker.checkToStart(now).orElseThrow(), new ServiceCheck(ServiceState.READY, "listed"), now);
    }

    private void ready(long now) {
        checkReady(now);
        tracker.onResult(answered(), now);
    }

    @Test
    void withoutKeyOrWithAnInvalidConfigurationTheServiceIsNeverChecked() {
        for (var state : List.of(ServiceState.UNCONFIGURED, ServiceState.INVALID_CONFIGURATION)) {
            var quiet = tracker(state);
            assertEquals(state, quiet.status().state());
            assertEquals(OptionalLong.empty(), quiet.checkToStart(0));
            assertFalse(quiet.allowsDispatch(0));
            quiet.onResult(failed(FailureKind.RATE_LIMITED), 0);
            assertEquals(state, quiet.status().state());
        }
    }

    @Test
    void aConfiguredStartChecksOnceAndAReadyCheckOpensATrialOfOneRequest() {
        assertEquals(ServiceState.CHECKING, tracker.status().state());
        var generation = tracker.checkToStart(0);
        assertTrue(generation.isPresent());
        assertEquals(OptionalLong.empty(), tracker.checkToStart(SECOND));
        tracker.onCheck(generation.getAsLong(), new ServiceCheck(ServiceState.READY, "listed"), SECOND);
        assertTrue(tracker.inTrial());
        assertTrue(tracker.allowsDispatch(0));
        assertFalse(tracker.allowsDispatch(1));
        tracker.onResult(answered(), SECOND);
        assertFalse(tracker.inTrial());
        assertTrue(tracker.allowsDispatch(7));
    }

    @Test
    void aCheckWithoutResultIsStartedAgainAfterItsTimeoutAndAnOlderCheckIsIgnored() {
        var first = tracker.checkToStart(0).orElseThrow();
        var second = tracker.checkToStart(CHECK_TIMEOUT).orElseThrow();
        assertTrue(second > first);
        tracker.onCheck(first, new ServiceCheck(ServiceState.READY, "late"), CHECK_TIMEOUT);
        assertEquals(ServiceState.CHECKING, tracker.status().state());
        tracker.onCheck(second, new ServiceCheck(ServiceState.READY, "listed"), CHECK_TIMEOUT);
        assertEquals(ServiceState.READY, tracker.status().state());
    }

    @Test
    void aRateLimitWithRetryAfterTwoSecondsResumesAfterTwoSecondsAndACheck() {
        ready(0);
        tracker.onResult(new DecisionResult.Failed(FailureKind.RATE_LIMITED, Optional.of(Duration.ofSeconds(2)),
                "HTTP 429: rate limited"), 10 * SECOND);
        assertEquals(ServiceState.PAUSED, tracker.status().state());
        assertFalse(tracker.allowsDispatch(0));
        assertEquals(OptionalLong.empty(), tracker.checkToStart(12 * SECOND - 1));
        var generation = tracker.checkToStart(12 * SECOND);
        assertTrue(generation.isPresent());
        assertEquals(ServiceState.CHECKING, tracker.status().state());
        tracker.onCheck(generation.getAsLong(), new ServiceCheck(ServiceState.READY, "listed"), 12 * SECOND);
        assertTrue(tracker.allowsDispatch(0));
    }

    @Test
    void overloadedNetworkAndServiceErrorsPauseWithTheCalculatedPause() {
        for (var kind : List.of(FailureKind.OVERLOADED, FailureKind.NETWORK, FailureKind.SERVICE_ERROR)) {
            var fresh = tracker(ServiceState.CHECKING);
            fresh.onCheck(fresh.checkToStart(0).orElseThrow(), new ServiceCheck(ServiceState.READY, "ok"), 0);
            fresh.onResult(failed(kind), 0);
            assertEquals(ServiceState.PAUSED, fresh.status().state(), kind.name());
            assertEquals(OptionalLong.empty(), fresh.checkToStart(SECOND - 1));
            assertTrue(fresh.checkToStart(SECOND).isPresent(), kind.name());
        }
    }

    @Test
    void thePauseDoublesWithEachFailedRecoveryUpToSixtySecondsTimesTheRandomFactor() {
        checkReady(0);
        var now = 0L;
        var expected = List.of(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L);
        for (var seconds : expected) {
            tracker.onResult(failed(FailureKind.SERVICE_ERROR), now);
            assertEquals(OptionalLong.empty(), tracker.checkToStart(now + seconds * SECOND - 1));
            now += seconds * SECOND;
            tracker.onCheck(tracker.checkToStart(now).orElseThrow(), new ServiceCheck(ServiceState.READY, "ok"), now);
        }
        random = 0.0;
        tracker.onResult(failed(FailureKind.SERVICE_ERROR), now);
        assertTrue(tracker.checkToStart(now + 48 * SECOND).isPresent());
        tracker.onResult(answered(), now);
    }

    @Test
    void anAnswerResetsTheStreakSoTheNextPauseStartsAtOneSecondAgain() {
        checkReady(0);
        tracker.onResult(failed(FailureKind.NETWORK), 0);
        tracker.onCheck(tracker.checkToStart(SECOND).orElseThrow(), new ServiceCheck(ServiceState.READY, "ok"), SECOND);
        tracker.onResult(failed(FailureKind.NETWORK), SECOND);
        tracker.onCheck(tracker.checkToStart(3 * SECOND).orElseThrow(), new ServiceCheck(ServiceState.READY, "ok"),
                3 * SECOND);
        tracker.onResult(answered(), 3 * SECOND);
        tracker.onResult(failed(FailureKind.NETWORK), 3 * SECOND);
        assertTrue(tracker.checkToStart(4 * SECOND).isPresent());
    }

    @Test
    void oneTimeoutDoesNotPauseButTwoInARowDo() {
        ready(0);
        tracker.onResult(failed(FailureKind.TIMEOUT), 0);
        assertEquals(ServiceState.READY, tracker.status().state());
        tracker.onResult(answered(), 0);
        tracker.onResult(failed(FailureKind.TIMEOUT), 0);
        assertEquals(ServiceState.READY, tracker.status().state());
        tracker.onResult(failed(FailureKind.TIMEOUT), 0);
        assertEquals(ServiceState.PAUSED, tracker.status().state());
    }

    @Test
    void invalidResponsesPauseOnlyAfterThreeInARow() {
        ready(0);
        tracker.onResult(failed(FailureKind.INVALID_RESPONSE), 0);
        tracker.onResult(failed(FailureKind.INVALID_RESPONSE), 0);
        assertEquals(ServiceState.READY, tracker.status().state());
        tracker.onResult(failed(FailureKind.INVALID_RESPONSE), 0);
        assertEquals(ServiceState.PAUSED, tracker.status().state());
    }

    @Test
    void anInvalidRequestPausesForTheMaximumPause() {
        ready(0);
        tracker.onResult(failed(FailureKind.INVALID_REQUEST), 0);
        assertEquals(OptionalLong.empty(), tracker.checkToStart(60 * SECOND - 1));
        assertTrue(tracker.checkToStart(60 * SECOND).isPresent());
    }

    @Test
    void rejectedCredentialsOrModelBlockUntilACheckIsRequested() {
        ready(0);
        tracker.onResult(failed(FailureKind.AUTHENTICATION), 0);
        assertEquals(ServiceState.INVALID_CREDENTIALS, tracker.status().state());
        assertEquals(OptionalLong.empty(), tracker.checkToStart(3600 * SECOND));
        tracker.requestCheck();
        tracker.onCheck(tracker.checkToStart(3600 * SECOND).orElseThrow(), new ServiceCheck(ServiceState.INVALID_MODEL,
                "not listed"), 3600 * SECOND);
        assertEquals(ServiceState.INVALID_MODEL, tracker.status().state());
        assertEquals(OptionalLong.empty(), tracker.checkToStart(7200 * SECOND));
        tracker.requestCheck();
        checkReady(7200 * SECOND);
        tracker.onResult(failed(FailureKind.MODEL_UNAVAILABLE), 7200 * SECOND);
        assertEquals(ServiceState.INVALID_MODEL, tracker.status().state());
    }

    @Test
    void aLocalFailureSaysNothingAboutTheService() {
        ready(0);
        for (var detail : ServiceStatusTracker.LOCAL_FAILURE_DETAILS) {
            tracker.onResult(new DecisionResult.Failed(FailureKind.NETWORK, Optional.empty(), detail), 0);
        }
        assertEquals(ServiceState.READY, tracker.status().state());
    }

    @Test
    void aPauseKeepsRequestsInFlightAndALaterFailureOnlyExtendsIt() {
        ready(0);
        tracker.onResult(failed(FailureKind.NETWORK), 0);
        tracker.onResult(new DecisionResult.Failed(FailureKind.RATE_LIMITED, Optional.of(Duration.ofSeconds(5)), "429"), 0);
        tracker.onResult(answered(), 0);
        assertEquals(ServiceState.PAUSED, tracker.status().state());
        assertEquals(OptionalLong.empty(), tracker.checkToStart(5 * SECOND - 1));
        assertTrue(tracker.checkToStart(5 * SECOND).isPresent());
    }

    @Test
    void aFailedCheckPausesAndARequestedCheckStartsAtOnce() {
        tracker.onCheck(tracker.checkToStart(0).orElseThrow(), new ServiceCheck(ServiceState.PAUSED, "HTTP 503"), 0);
        assertEquals(ServiceState.PAUSED, tracker.status().state());
        tracker.requestCheck();
        assertEquals(ServiceState.CHECKING, tracker.status().state());
        assertTrue(tracker.checkToStart(0).isPresent());
    }

    @Test
    void eachChangeOfStateIsLoggedOnceWithoutTheDetailOfTheService() {
        ready(0);
        tracker.onResult(failed(FailureKind.RATE_LIMITED), 0);
        tracker.onResult(failed(FailureKind.RATE_LIMITED), 0);
        assertEquals(2, log.size());
        assertEquals("Jev service checking -> ready (check ready, trial)", log.getFirst());
        assertTrue(log.get(1).startsWith("Jev service ready -> paused (rate_limited, "), log.get(1));
        assertTrue(log.stream().noneMatch(line -> line.contains("HTTP detail")), log.toString());
    }
}
