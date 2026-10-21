package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.engine.Preparation;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static akyrion.thepredict.adapter.jev.JevFixtures.ENCOUNTER;
import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_A;
import static akyrion.thepredict.adapter.jev.JevFixtures.guardian;
import static akyrion.thepredict.adapter.jev.JevFixtures.question;
import static akyrion.thepredict.adapter.jev.SystemOneContractServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemOneClientTest {
    private final SystemOneContractServer server = SystemOneContractServer.start();
    private final List<SystemOneClient> clients = new ArrayList<>();

    @AfterEach
    void stop() {
        clients.forEach(SystemOneClient::close);
        server.close();
    }

    @Test
    void aDecisionSendsTheContractRequestAndReturnsTheAnswers() throws Exception {
        var result = answered(JevFixtures.send(client(), JevFixtures.request()));
        assertEquals("jev-1.13.0", result.effectiveModel());
        assertEquals(412, result.inputTokens());
        assertEquals(JevFixtures.tactic("keep_range"), result.answers().get(QuestionKey.of(0)).best());
        var request = server.received().getFirst();
        assertEquals("POST", request.method());
        assertEquals("/v1/systemone", request.path());
        assertEquals("Bearer " + JevFixtures.KEY, request.header("Authorization"));
        assertEquals("application/json", request.header("Content-Type"));
        assertEquals("application/json", request.header("Accept"));
        assertEquals("ThePredict/0.0.1", request.header("User-Agent"));
        var body = JsonParser.parseString(request.body()).getAsJsonObject();
        assertEquals("jev-1.13.0", body.get("model").getAsString());
        assertEquals(List.of("q0", "q1"), List.copyOf(body.getAsJsonObject("questions").keySet()));
        assertFalse(request.body().contains(JevFixtures.KEY));
    }

    @Test
    void aDifferentEffectiveModelIsReportedAsReceived() throws Exception {
        server.onDecide(json(200, SystemOneContractServer.fixture("response-valid.json")
                .replace("\"jev-1.13.0\"", "\"jev-1.14.2\"")));
        assertEquals("jev-1.14.2", answered(JevFixtures.send(client(), JevFixtures.request())).effectiveModel());
    }

    @Test
    void prepareEncodesOnTheCallingThreadAndDecideSendsThePreparedBodyWithoutEncodingAgain() throws Exception {
        var preparations = new CopyOnWriteArrayList<String>();
        var real = new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000));
        var client = client(JevFixtures.settings(server.baseUrl()), request -> {
            preparations.add(Thread.currentThread().getName());
            return real.prepare(request);
        });
        var prepared = assertInstanceOf(Preparation.Ready.class, client.prepare(JevFixtures.request())).prepared();
        answered(client.decide(prepared));
        assertEquals(List.of(Thread.currentThread().getName()), preparations);
        assertEquals(prepared.body(), server.received().getFirst().body());
        assertEquals(prepared.bodyChars(), server.received().getFirst().body().length());
    }

    @Test
    void aResponseThatNeverStartsCompletesWithTimeout() throws Exception {
        server.onDecide(SystemOneContractServer.stallBeforeHeaders());
        var client = client(JevFixtures.settings(server.baseUrl(), Duration.ofMillis(400)));
        assertFailure(FailureKind.TIMEOUT, "no response within the request timeout",
                JevFixtures.send(client, JevFixtures.request()));
    }

    @Test
    void aBodyThatStallsAfterTheHeadersTimesOutAfterTheMarginAndFreesTheExecutor() throws Exception {
        server.onDecide(SystemOneContractServer.stallAfterHeaders());
        var client = client(JevFixtures.settings(server.baseUrl(), Duration.ofMillis(400)));
        var first = JevFixtures.send(client, JevFixtures.request());
        var second = JevFixtures.send(client, JevFixtures.request());
        assertFailure(FailureKind.TIMEOUT, "no response within the request timeout", first);
        assertFailure(FailureKind.TIMEOUT, "no response within the request timeout", second);
        server.onDecide(json(200, SystemOneContractServer.fixture("response-valid.json")));
        assertEquals(412, answered(JevFixtures.send(client, JevFixtures.request())).inputTokens());
    }

    @Test
    void aBodyLargerThanTheLimitIsAnInvalidResponse() throws Exception {
        server.onDecide(SystemOneContractServer.chunked(300_000));
        assertFailure(FailureKind.INVALID_RESPONSE, "response body exceeds maxResponseBytes",
                JevFixtures.send(client(), JevFixtures.request()));
        server.onDecide(json(200, " ".repeat(300_000)));
        assertFailure(FailureKind.INVALID_RESPONSE, "response body exceeds maxResponseBytes",
                JevFixtures.send(client(), JevFixtures.request()));
    }

    @Test
    void statusesAreClassifiedAndNeverRetried() throws Exception {
        var client = client();
        var cases = Map.of(401, FailureKind.AUTHENTICATION, 403, FailureKind.AUTHENTICATION,
                500, FailureKind.SERVICE_ERROR, 503, FailureKind.SERVICE_ERROR, 529, FailureKind.OVERLOADED);
        var posts = 0L;
        for (var entry : cases.entrySet()) {
            server.onDecide(json(entry.getKey(), "{}"));
            assertEquals(entry.getValue(), failed(JevFixtures.send(client, JevFixtures.request())).kind(), "HTTP " + entry.getKey());
            assertEquals(++posts, server.posts(), "no retry after HTTP " + entry.getKey());
        }
    }

    @Test
    void validationErrorsDistinguishTheModelFromTheRestOfTheRequest() throws Exception {
        server.onDecide(json(422, SystemOneContractServer.fixture("validation-model.json")));
        assertFailure(FailureKind.MODEL_UNAVAILABLE, "HTTP 422: the service rejected the model",
                JevFixtures.send(client(), JevFixtures.request()));
        server.onDecide(json(422, SystemOneContractServer.fixture("validation-other.json")));
        assertFailure(FailureKind.INVALID_REQUEST,
                "HTTP 422: the service rejected the request at body.questions.q0.criteria",
                JevFixtures.send(client(), JevFixtures.request()));
    }

    @Test
    void theRealBodiesOfAnUnknownModelAndOfARejectedKeyAreClassifiedOverHttp() throws Exception {
        var client = client();
        server.onDecide(json(400, SystemOneContractServer.fixture("error-400-unknown-model.json")));
        assertFailure(FailureKind.MODEL_UNAVAILABLE, FailureClassifier.UNKNOWN_MODEL, JevFixtures.send(client, JevFixtures.request()));
        server.onDecide(json(401, SystemOneContractServer.fixture("error-401.json")));
        assertFailure(FailureKind.AUTHENTICATION, "HTTP 401: the service rejected the credentials",
                JevFixtures.send(client, JevFixtures.request()));
        server.onModels(json(401, SystemOneContractServer.fixture("error-401.json")));
        assertEquals(ServiceState.INVALID_CREDENTIALS, client.check().get(10, TimeUnit.SECONDS).state());
    }

    @Test
    void rateLimitingCarriesRetryAfter() throws Exception {
        server.onDecide(json(429, "{}", Map.of("retry-after", "2")));
        assertEquals(new DecisionResult.Failed(FailureKind.RATE_LIMITED, Optional.of(Duration.ofSeconds(2)),
                "HTTP 429: rate limited"), failed(JevFixtures.send(client(), JevFixtures.request())));
    }

    @Test
    void malformedJsonAnUnknownOptionAndInvalidProbabilitiesAreInvalidResponses() throws Exception {
        var valid = SystemOneContractServer.fixture("response-valid.json");
        server.onDecide(json(200, "{\"model\": \"jev-1.13.0\", \"answers\": "));
        assertFailure(FailureKind.INVALID_RESPONSE, "response is not valid JSON", JevFixtures.send(client(), JevFixtures.request()));
        server.onDecide(json(200, valid.replace("\"hold_ground\": 0.1", "\"summon_minions\": 0.1")));
        assertFailure(FailureKind.INVALID_RESPONSE, "answers.q0.probabilities must name exactly the options sent",
                JevFixtures.send(client(), JevFixtures.request()));
        server.onDecide(json(200, valid.replace("\"keep_range\": 0.7", "\"keep_range\": 0.9")));
        assertFailure(FailureKind.INVALID_RESPONSE, "answers.q0.probabilities must sum to 1 within 0.02",
                JevFixtures.send(client(), JevFixtures.request()));
        server.onDecide(json(200, valid.replace("\"confidence\": 0.71,", "")));
        assertFailure(FailureKind.INVALID_RESPONSE, "answers.q0.confidence must be a number",
                JevFixtures.send(client(), JevFixtures.request()));
    }

    @Test
    void aConnectionCutBeforeTheResponseIsANetworkFailureWithoutRetry() throws Exception {
        server.onDecide(SystemOneContractServer.cut());
        assertEquals(FailureKind.NETWORK, failed(JevFixtures.send(client(), JevFixtures.request())).kind());
        assertEquals(1, server.posts());
    }

    @Test
    void anInvalidRequestIsNeverSent() throws Exception {
        var undeclared = new DecisionRequest(ENCOUNTER, 1, JevFixtures.meleeFacts(),
                List.of(question(0, MOB_A, Situation.NOW, "hold_ground", "summon_minions")), Map.of(MOB_A, guardian()));
        assertFailure(FailureKind.INVALID_REQUEST, "option summon_minions is not declared by the profile of mob_1",
                JevFixtures.send(client(), undeclared));
        var tiny = new SystemOneSettings(server.baseUrl(), "jev-1.13.0", JevFixtures.KEY, Duration.ofSeconds(2),
                Duration.ofSeconds(5), 262_144, 100, "0.0.1");
        assertFailure(FailureKind.INVALID_REQUEST, "state exceeds maxStateChars even after trimming",
                JevFixtures.send(client(tiny), JevFixtures.request()));
        assertEquals(0, server.posts());
    }

    @Test
    void cancellingBeforeTheResponseLeavesTheFutureCancelledAndTheClientUsable() throws Exception {
        server.onDecide(SystemOneContractServer.stallBeforeHeaders());
        var client = client();
        var pending = JevFixtures.send(client, JevFixtures.request());
        server.awaitArrival();
        assertTrue(pending.cancel(true));
        assertTrue(pending.isCancelled());
        assertThrows(CancellationException.class, () -> pending.get(1, TimeUnit.SECONDS));
        server.onDecide(json(200, SystemOneContractServer.fixture("response-valid.json")));
        assertEquals(412, answered(JevFixtures.send(client, JevFixtures.request())).inputTokens());
    }

    @Test
    void closingWhileARequestIsInFlightCompletesItAndStopsTheExecutor() throws Exception {
        server.onDecide(SystemOneContractServer.stallBeforeHeaders());
        var threads = new CopyOnWriteArrayList<Thread>();
        var client = transporting(JevFixtures.settings(server.baseUrl()), (http, request, handler) -> {
            threads.add(Thread.currentThread());
            return http.sendAsync(request, handler);
        });
        var pending = JevFixtures.send(client, JevFixtures.request());
        server.awaitArrival();
        client.close();
        assertFailure(FailureKind.NETWORK, SystemOneClient.CLOSED, pending);
        assertTrue(client.executorTerminated());
        assertFalse(threads.isEmpty());
        for (var thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(10));
            assertFalse(thread.isAlive(), thread.getName());
        }
    }

    @Test
    void callsAfterCloseAnswerAtOnceWithoutContactingTheService() throws Exception {
        var client = client();
        client.close();
        client.close();
        var decision = JevFixtures.send(client, JevFixtures.request());
        assertTrue(decision.isDone());
        assertFailure(FailureKind.NETWORK, SystemOneClient.CLOSED, decision);
        var check = client.check();
        assertTrue(check.isDone());
        assertEquals(ServiceState.PAUSED, check.get().state());
        assertEquals(SystemOneClient.CLOSED, check.get().detail());
        assertTrue(server.received().isEmpty());
    }

    @Test
    void timingDiagnosticsAreRateLimitedAndCarryNoSecret() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        var client = track(new SystemOneClient(JevFixtures.settings(server.baseUrl()), JevFixtures.CLOCK, lines::add));
        answered(JevFixtures.send(client, JevFixtures.request()));
        answered(JevFixtures.send(client, JevFixtures.request()));
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.getFirst().startsWith("SystemOne timings: parse "), lines.getFirst());
        assertFalse(lines.getFirst().contains(JevFixtures.KEY));
    }

    @Test
    void noFailureDetailContainsTheKeyEvenWhenTheServiceEchoesTheHeader() throws Exception {
        var client = client();
        for (var status : List.of(200, 401, 403, 422, 429, 500, 529)) {
            server.onDecide(SystemOneContractServer.echoingAuthorization(status));
            var detail = failed(JevFixtures.send(client, JevFixtures.request())).detail();
            assertFalse(detail.contains(JevFixtures.KEY), "HTTP " + status + ": " + detail);
            server.onModels(SystemOneContractServer.echoingAuthorization(status));
            var check = client.check().get(10, TimeUnit.SECONDS).detail();
            assertFalse(check.contains(JevFixtures.KEY), "check HTTP " + status + ": " + check);
        }
        assertFalse(client.toString().contains(JevFixtures.KEY));
    }

    @Test
    void aFullQueueFailsAtOnceWithoutHarmingTheHttpClientAndTheClientRecovers() throws Exception {
        var entered = new CountDownLatch(SystemOneExecutor.THREADS);
        var release = new CountDownLatch(1);
        var blocking = new AtomicBoolean(true);
        var client = transporting(JevFixtures.settings(server.baseUrl(), Duration.ofSeconds(30)),
                (http, request, handler) -> {
                    if (blocking.get()) {
                        entered.countDown();
                        await(release);
                    }
                    return http.sendAsync(request, handler);
                });
        var accepted = fillQueue(client, entered);
        assertFailure(FailureKind.NETWORK, FailureClassifier.QUEUE_FULL, JevFixtures.send(client, JevFixtures.request()));
        var rejectedCheck = client.check();
        assertTrue(rejectedCheck.isDone());
        assertEquals(new ServiceCheck(ServiceState.PAUSED, FailureClassifier.QUEUE_FULL), rejectedCheck.get());
        blocking.set(false);
        release.countDown();
        for (var future : accepted) {
            assertEquals(412, answered(future).inputTokens());
        }
        assertEquals(412, answered(JevFixtures.send(client, JevFixtures.request())).inputTokens());
        assertEquals(ServiceState.READY, client.check().get(10, TimeUnit.SECONDS).state());
        assertEquals(accepted.size() + 1, server.posts());
    }

    @Test
    void aCheckMayTakeTheConnectionTimeoutWhileADecisionKeepsItsOwnDeadline() throws Exception {
        server.onModels(delayed(json(200, SystemOneContractServer.fixture("models.json")), 1200));
        server.onDecide(delayed(json(200, SystemOneContractServer.fixture("response-valid.json")), 1200));
        var client = client(JevFixtures.settings(server.baseUrl(), Duration.ofMillis(300)));
        assertEquals(ServiceState.READY, client.check().get(10, TimeUnit.SECONDS).state(),
                "the check gets the connection timeout of 2 s on top of the request timeout");
        assertEquals(FailureKind.TIMEOUT, failed(JevFixtures.send(client, JevFixtures.request())).kind());
    }

    @Test
    void aGroupPlanAskedByTheFactsComesBackWithTheEpochOfTheirBrief() throws Exception {
        var g0 = "\"g0\": {\"type\": \"choice\", \"choice\": \"surround_target\", \"confidence\": 0.7,"
                + " \"probabilities\": {\"press_main_target\": 0.3, \"surround_target\": 0.7}},";
        server.onDecide(json(200, SystemOneContractServer.fixture("response-valid.json")
                .replace("\"answers\": {", "\"answers\": {" + g0)));
        var facts = JevFixtures.meleeFacts().withGroup(GroupRelations.NONE, new GroupBrief(
                GroupQuestions.PLAN_AND_LEADER, List.of(GroupPlan.PRESS_MAIN_TARGET.option(),
                GroupPlan.SURROUND_TARGET.option()), 7L, true));
        var base = JevFixtures.request();
        var request = new DecisionRequest(base.encounter(), base.sequence(), facts, base.questions(), base.profiles());
        var answered = assertInstanceOf(DecisionResult.Answered.class,
                JevFixtures.send(client(), request).get(10, TimeUnit.SECONDS));
        assertEquals(GroupPlan.SURROUND_TARGET, answered.group().orElseThrow().plan());
        assertEquals(7L, answered.group().orElseThrow().membershipEpoch());
    }

    @Test
    void theRootIsWarnedOnlyWhenTheJdkWouldLogTheAuthorizationHeader() {
        assertTrue(SystemOneClient.headerLogWarning("headers").isPresent());
        assertTrue(SystemOneClient.headerLogWarning("requests,HEADERS").isPresent());
        assertTrue(SystemOneClient.headerLogWarning("all").isPresent());
        assertTrue(SystemOneClient.headerLogWarning("").isEmpty());
        assertTrue(SystemOneClient.headerLogWarning("errors,requests").isEmpty());
        assertFalse(SystemOneClient.headerLogWarning("headers").orElseThrow().contains(JevFixtures.KEY));
    }

    private static SystemOneContractServer.Reply delayed(SystemOneContractServer.Reply reply, long millis) {
        return (exchange, request, released) -> {
            try {
                released.await(millis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            reply.send(exchange, request, released);
        };
    }

    private static List<CompletableFuture<DecisionResult>> fillQueue(SystemOneClient client, CountDownLatch entered)
            throws InterruptedException {
        var accepted = new ArrayList<CompletableFuture<DecisionResult>>();
        for (var index = 0; index < SystemOneExecutor.THREADS; index++) {
            accepted.add(JevFixtures.send(client, JevFixtures.request()));
        }
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        for (var index = 0; index < SystemOneExecutor.QUEUE_CAPACITY; index++) {
            accepted.add(JevFixtures.send(client, JevFixtures.request()));
        }
        accepted.forEach(future -> assertFalse(future.isDone()));
        return accepted;
    }

    @Test
    void aRequestCancelledWhileQueuedIsNeverSent() throws Exception {
        var entered = new CountDownLatch(SystemOneExecutor.THREADS);
        var release = new CountDownLatch(1);
        var sent = new CopyOnWriteArrayList<String>();
        var client = transporting(JevFixtures.settings(server.baseUrl()), (http, request, handler) -> {
            sent.add(request.uri().getPath());
            entered.countDown();
            await(release);
            return http.sendAsync(request, handler);
        });
        var first = JevFixtures.send(client, JevFixtures.request());
        var second = JevFixtures.send(client, JevFixtures.request());
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        var queued = JevFixtures.send(client, sequence(99));
        assertTrue(queued.cancel(true));
        release.countDown();
        answered(first);
        answered(second);
        client.close();
        assertEquals(2, sent.size(), sent.toString());
        assertEquals(2, server.posts());
    }

    private static DecisionRequest sequence(long sequence) {
        var request = JevFixtures.request();
        return new DecisionRequest(request.encounter(), sequence, request.facts(), request.questions(),
                request.profiles());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private SystemOneClient client() {
        return client(JevFixtures.settings(server.baseUrl()));
    }

    private SystemOneClient client(SystemOneSettings settings) {
        return track(new SystemOneClient(settings, JevFixtures.CLOCK));
    }

    private SystemOneClient client(SystemOneSettings settings, Function<DecisionRequest, Preparation> preparer) {
        return track(new SystemOneClient(settings, JevFixtures.CLOCK, line -> {
        }, Clock.systemUTC(), preparer));
    }

    private SystemOneClient transporting(SystemOneSettings settings, AbortableExchange.Transport transport) {
        return track(new SystemOneClient(settings, JevFixtures.CLOCK, line -> {
        }, Clock.systemUTC(), new RequestEncoder(settings.model(), new SemanticEncoder(12_000))::prepare, transport));
    }

    private SystemOneClient track(SystemOneClient client) {
        clients.add(client);
        return client;
    }

    private static DecisionResult.Answered answered(CompletableFuture<DecisionResult> future) throws Exception {
        return assertInstanceOf(DecisionResult.Answered.class, future.get(10, TimeUnit.SECONDS));
    }

    private static DecisionResult.Failed failed(CompletableFuture<DecisionResult> future) throws Exception {
        return assertInstanceOf(DecisionResult.Failed.class, future.get(10, TimeUnit.SECONDS));
    }

    private static void assertFailure(FailureKind kind, String detail, CompletableFuture<DecisionResult> future)
            throws Exception {
        var failure = failed(future);
        assertEquals(kind, failure.kind(), failure.detail());
        assertEquals(detail, failure.detail());
    }

    @Test
    void theFitChecksOfAFullRequestTravelInTheSameCallAndComeBackAsFits() throws Exception {
        server.onDecide(json(200, SystemOneContractServer.fixture("response-with-fits.json")));
        var request = JevFixtures.request();
        var profile = JevFixtures.detailed(guardian(), QuestionDetail.FULL, true);
        var checks = List.of(new FitCheck(QuestionKey.of(2), MOB_A,
                        JevFixtures.tactic("press_attack")),
                new FitCheck(QuestionKey.of(3), MOB_A, JevFixtures.tactic("keep_range")),
                new FitCheck(QuestionKey.of(4), MOB_A, JevFixtures.tactic("hold_ground")));
        var result = answered(JevFixtures.send(client(), new DecisionRequest(ENCOUNTER, 4, request.facts(),
                request.questions(), Map.of(MOB_A, profile), checks)));
        assertEquals(1, server.posts());
        assertEquals(Map.of(QuestionKey.of(2), 0.12, QuestionKey.of(3), 0.81, QuestionKey.of(4), 0.4), result.fits());
        var body = JsonParser.parseString(server.received().getFirst().body()).getAsJsonObject();
        assertEquals("noul", body.getAsJsonObject("questions").getAsJsonObject("q3").get("type").getAsString());
    }
}
