package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.engine.ServiceStatusTracker;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureClassifierTest {
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
    private static final HttpHeaders NONE = headers(Map.of());
    private final FailureClassifier classifier = new FailureClassifier(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void credentialsRejectionsAreAuthentication() {
        assertFailure(FailureKind.AUTHENTICATION, "HTTP 401: the service rejected the credentials",
                classifier.fromStatus(401, NONE, ""));
        assertFailure(FailureKind.AUTHENTICATION, "HTTP 403: the service rejected the credentials",
                classifier.fromStatus(403, NONE, ""));
    }

    @Test
    void theRealUnknownModelAnswerIsHttp400AndIsModelUnavailableWithoutEchoingTheModel() {
        var failure = classifier.fromStatus(400, NONE, SystemOneContractServer.fixture("error-400-unknown-model.json"));
        assertFailure(FailureKind.MODEL_UNAVAILABLE, FailureClassifier.UNKNOWN_MODEL, failure);
        assertFalse(failure.detail().contains("jev-0.0.0"), failure.detail());
    }

    @Test
    void anyOtherHttp400IsAnInvalidRequestWithAFixedDetail() {
        for (var body : List.of("{\"detail\":{\"error_type\":\"api_usage_error\",\"message\":\"secret-marker\"}}",
                "{\"detail\":{\"error_type\":\"other_error\",\"message\":\"Unknown model: secret-marker\"}}",
                "{\"detail\":\"Unknown model: secret-marker\"}", "{\"detail\":{\"error_type\":\"api_usage_error\"}}",
                "not json secret-marker", "")) {
            assertFailure(FailureKind.INVALID_REQUEST, "HTTP 400: the service rejected the request",
                    classifier.fromStatus(400, NONE, body));
        }
    }

    @Test
    void theRealObjectShapedHttp401BodyIsAuthenticationAndNeverBreaksTheParsing() {
        var body = SystemOneContractServer.fixture("error-401.json");
        assertFailure(FailureKind.AUTHENTICATION, "HTTP 401: the service rejected the credentials",
                classifier.fromStatus(401, NONE, body));
        assertFailure(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request",
                classifier.fromStatus(422, NONE, body));
        assertFailure(FailureKind.INVALID_REQUEST, "HTTP 400: the service rejected the request",
                classifier.fromStatus(400, NONE, body));
    }

    @Test
    void aValidationErrorOnTheModelIsModelUnavailableAndAnyOtherIsInvalidRequest() {
        assertFailure(FailureKind.MODEL_UNAVAILABLE, "HTTP 422: the service rejected the model",
                classifier.fromStatus(422, NONE, SystemOneContractServer.fixture("validation-model.json")));
        assertFailure(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request at body.questions.q0.criteria",
                classifier.fromStatus(422, NONE, SystemOneContractServer.fixture("validation-other.json")));
        assertFailure(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request",
                classifier.fromStatus(422, NONE, "not json"));
    }

    @Test
    void aValidationDetailKeepsOnlyPlainFieldNamesAndNeverMessagesOrInputs() {
        var body = "{\"detail\":[{\"loc\":[\"body\",\"state\",\"Bearer test-key-7f3a9c\",0],\"msg\":\"secret-marker\","
                + "\"input\":\"secret-marker\"}]}";
        var failure = classifier.fromStatus(422, NONE, body);
        assertEquals("HTTP 422: the service rejected the request at body.state.0", failure.detail());
        assertFalse(failure.detail().contains("secret-marker"));
        assertFalse(failure.detail().contains(JevFixtures.KEY));
    }

    @Test
    void onlyTheExactModelLocationIsModelUnavailable() {
        var nested = "{\"detail\":[{\"loc\":[\"body\",\"questions\",\"q0\",\"model\"],\"msg\":\"x\",\"type\":\"t\"}]}";
        assertFailure(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request at body.questions.q0.model",
                classifier.fromStatus(422, NONE, nested));
        var query = "{\"detail\":[{\"loc\":[\"query\",\"model\"],\"msg\":\"x\",\"type\":\"t\"}]}";
        assertEquals(FailureKind.INVALID_REQUEST, classifier.fromStatus(422, NONE, query).kind());
        var numbered = "{\"detail\":[{\"loc\":[\"body\",\"model\",0],\"msg\":\"x\",\"type\":\"t\"}]}";
        assertEquals(FailureKind.INVALID_REQUEST, classifier.fromStatus(422, NONE, numbered).kind());
    }

    @Test
    void aLocationWithoutPlainFieldNamesLeavesNoDanglingAt() {
        var hidden = "{\"detail\":[{\"loc\":[\"Bearer x-y\",\"a b\"],\"msg\":\"x\",\"type\":\"t\"},"
                + "{\"loc\":[],\"msg\":\"x\",\"type\":\"t\"}]}";
        assertFailure(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request",
                classifier.fromStatus(422, NONE, hidden));
        var second = "{\"detail\":[{\"loc\":[\"a b\"],\"msg\":\"x\",\"type\":\"t\"},"
                + "{\"loc\":[\"body\",\"state\"],\"msg\":\"x\",\"type\":\"t\"}]}";
        assertFailure(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request at body.state",
                classifier.fromStatus(422, NONE, second));
    }

    @Test
    void rejectedWorkIsTheFullQueue() {
        assertFailure(FailureKind.NETWORK, FailureClassifier.QUEUE_FULL,
                classifier.fromThrowable(new CompletionException(new RejectedExecutionException())));
    }

    @Test
    void theFailuresTheClientProducesItselfAreTheLocalFailuresOfTheServiceTracker() {
        assertTrue(ServiceStatusTracker.LOCAL_FAILURE_DETAILS.containsAll(Set.of(FailureClassifier.CANCELLED,
                FailureClassifier.QUEUE_FULL, SystemOneClient.CLOSED)));
        assertEquals(3, ServiceStatusTracker.LOCAL_FAILURE_DETAILS.size());
    }

    @Test
    void rateLimitAndOverloadCarryTheirRetryAfter() {
        var limited = classifier.fromStatus(429, headers(Map.of("retry-after", List.of("2"))), "");
        assertEquals(new DecisionResult.Failed(FailureKind.RATE_LIMITED, Optional.of(Duration.ofSeconds(2)),
                "HTTP 429: rate limited"), limited);
        var overloaded = classifier.fromStatus(529, headers(Map.of("Retry-After",
                List.of("Tue, 29 Sep 2026 10:00:30 GMT"))), "");
        assertEquals(new DecisionResult.Failed(FailureKind.OVERLOADED, Optional.of(Duration.ofSeconds(30)),
                "HTTP 529: service overloaded"), overloaded);
        assertEquals(Optional.empty(), classifier.fromStatus(429, NONE, "").retryAfter());
        assertEquals(Optional.empty(), classifier.fromStatus(529, headers(Map.of("retry-after", List.of("soon"))), "")
                .retryAfter());
    }

    @Test
    void otherStatusesAreServiceErrors() {
        assertFailure(FailureKind.SERVICE_ERROR, "HTTP 500: service error", classifier.fromStatus(500, NONE, ""));
        assertFailure(FailureKind.SERVICE_ERROR, "HTTP 503: service error", classifier.fromStatus(503, NONE, ""));
        assertFailure(FailureKind.SERVICE_ERROR, "HTTP 404: unexpected status", classifier.fromStatus(404, NONE, ""));
        assertFailure(FailureKind.SERVICE_ERROR, "HTTP 302: unexpected status", classifier.fromStatus(302, NONE, ""));
    }

    @Test
    void exceptionsAreClassifiedThroughTheirWrappers() {
        assertEquals(FailureKind.TIMEOUT, classifier.fromThrowable(new HttpTimeoutException("x")).kind());
        assertEquals(FailureKind.TIMEOUT, classifier.fromThrowable(new HttpConnectTimeoutException("x")).kind());
        assertEquals(FailureKind.TIMEOUT, classifier.fromThrowable(new CompletionException(new TimeoutException())).kind());
        assertFailure(FailureKind.NETWORK, "network failure (ConnectException)",
                classifier.fromThrowable(new ExecutionException(new ConnectException("refused"))));
        assertFailure(FailureKind.NETWORK, "network failure (IOException)",
                classifier.fromThrowable(new UncheckedIOException(new IOException("reset"))));
        assertFailure(FailureKind.NETWORK, FailureClassifier.CANCELLED,
                classifier.fromThrowable(new CompletionException(new CancellationException())));
        assertFailure(FailureKind.INVALID_RESPONSE, "rule",
                classifier.fromThrowable(new CompletionException(new SystemOneProtocolException("rule"))));
        assertFailure(FailureKind.INVALID_REQUEST, "rule", classifier.fromThrowable(new SystemOneRequestException("rule")));
    }

    @Test
    void noDetailEverContainsTheKeyEvenWhenTheServiceEchoesIt() {
        var echo = "{\"detail\":\"Bearer " + JevFixtures.KEY + "\"}";
        for (var status : List.of(401, 403, 404, 422, 429, 500, 529)) {
            assertFalse(classifier.fromStatus(status, headers(Map.of("retry-after", List.of(JevFixtures.KEY))), echo)
                    .detail().contains(JevFixtures.KEY), "status " + status);
        }
        assertFalse(classifier.fromThrowable(new IOException("Bearer " + JevFixtures.KEY)).detail()
                .contains(JevFixtures.KEY));
    }

    private static void assertFailure(FailureKind kind, String detail, DecisionResult.Failed failure) {
        assertEquals(kind, failure.kind());
        assertEquals(detail, failure.detail());
    }

    private static HttpHeaders headers(Map<String, List<String>> values) {
        return HttpHeaders.of(values, (name, value) -> true);
    }
}
