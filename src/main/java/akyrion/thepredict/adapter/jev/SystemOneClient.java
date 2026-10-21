package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.PreparedRequest;
import akyrion.thepredict.engine.ServerClock;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Direct client of the TypeSafe System One protocol (M08): {@code POST /v1/systemone} for decisions and
 * {@code GET /v1/models} for checks. One {@link HttpClient} and one bounded executor per instance; the root
 * creates one instance per process. Sending and parsing run on the bounded executor, which also admits work:
 * when its queue is full a call fails at once with {@code client queue is full}. The {@link HttpClient} keeps
 * the JDK's own executor, which never rejects, because a rejected transport task closes the JDK selector and
 * would kill the client for good. {@link #prepare} encodes and deduplicates the request on the caller's thread without
 * any I/O (M16.1, rule T2); {@link #decide} only builds a chain of futures that sends the prepared body as it is, never
 * encoding it again, and a request whose future already completed (timeout, cancellation or close) is never sent. No
 * automatic retries. With
 * {@code debug.recordScenarios} on, each answered decision is handed to a {@link ScenarioRecorder} after its result was
 * completed, and the recorder writes it on its own thread (M13.6). Thread-safe.
 */
public final class SystemOneClient implements DecisionClient {
    /** Extra time after the request timeout before a future is completed with {@code TIMEOUT}. */
    static final Duration TIMEOUT_MARGIN = Duration.ofMillis(200);
    /** Detail of every result produced after {@link #close()}. */
    static final String CLOSED = "client closed";

    private final SystemOneSettings settings;
    private final SystemOneExecutor executor;
    private final HttpClient http;
    private final String authorization;
    private final String userAgent;
    private final FailureClassifier classifier;
    private final Function<DecisionRequest, Preparation> preparer;
    private final AbortableExchange.Transport transport;
    private final ScenarioRecorder recorder;
    private final TimingLog timings;
    private final PendingCalls calls;

    /**
     * Creates the client without timing diagnostics. Opens no connection until the first call.
     *
     * @param settings translated settings
     * @param clock monotonic clock for the internal timings
     */
    public SystemOneClient(SystemOneSettings settings, ServerClock clock) {
        this(settings, clock, line -> {
        });
    }

    /**
     * Creates the client without scenario recording. Opens no connection until the first call; the first
     * {@link #check()} opens the HTTP/2 connection that later decisions reuse.
     *
     * @param settings translated settings
     * @param clock monotonic clock for the internal timings
     * @param diagnostics rate-limited sink of timing lines, used only when {@code decision.logDecisions} is on
     */
    public SystemOneClient(SystemOneSettings settings, ServerClock clock, Consumer<String> diagnostics) {
        this(settings, clock, diagnostics, ScenarioRecorder.disabled());
    }

    /**
     * Creates the client. Opens no connection until the first call; the first {@link #check()} opens the
     * HTTP/2 connection that later decisions reuse.
     *
     * @param settings translated settings
     * @param clock monotonic clock for the internal timings
     * @param diagnostics rate-limited sink of timing lines, used only when {@code decision.logDecisions} is on
     * @param recorder scenario bank of M13.6, {@link ScenarioRecorder#disabled()} unless
     *                 {@code debug.recordScenarios} is on
     */
    public SystemOneClient(SystemOneSettings settings, ServerClock clock, Consumer<String> diagnostics,
                           ScenarioRecorder recorder) {
        this(settings, clock, diagnostics, Clock.systemUTC(),
                new RequestEncoder(settings.model(), new SemanticEncoder(settings.maxStateChars()))::prepare, recorder);
    }

    SystemOneClient(SystemOneSettings settings, ServerClock clock, Consumer<String> diagnostics, Clock wallClock,
                    Function<DecisionRequest, Preparation> preparer) {
        this(settings, clock, diagnostics, wallClock, preparer, ScenarioRecorder.disabled());
    }

    SystemOneClient(SystemOneSettings settings, ServerClock clock, Consumer<String> diagnostics, Clock wallClock,
                    Function<DecisionRequest, Preparation> preparer, ScenarioRecorder recorder) {
        this(settings, clock, diagnostics, wallClock, preparer, HttpClient::sendAsync, recorder);
    }

    SystemOneClient(SystemOneSettings settings, ServerClock clock, Consumer<String> diagnostics, Clock wallClock,
                    Function<DecisionRequest, Preparation> preparer, AbortableExchange.Transport transport) {
        this(settings, clock, diagnostics, wallClock, preparer, transport, ScenarioRecorder.disabled());
    }

    private SystemOneClient(SystemOneSettings settings, ServerClock clock, Consumer<String> diagnostics,
                            Clock wallClock, Function<DecisionRequest, Preparation> preparer,
                            AbortableExchange.Transport transport, ScenarioRecorder recorder) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = new SystemOneExecutor();
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
                .connectTimeout(settings.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        this.authorization = "Bearer " + settings.apiKey();
        this.userAgent = "ThePredict/" + settings.pluginVersion();
        this.classifier = new FailureClassifier(wallClock);
        this.preparer = Objects.requireNonNull(preparer, "preparer");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.timings = new TimingLog(clock, diagnostics);
        this.calls = new PendingCalls(executor.executor(), settings.requestTimeout().plus(TIMEOUT_MARGIN));
    }

    /**
     * The warning the root logs at start when the JDK HTTP client is told to log headers
     * ({@code -Djdk.httpclient.HttpClient.log} with {@code headers} or {@code all}): the JDK would then write the
     * {@code Authorization} header to its own log (rules C8 and W6). It reads a system property, never the
     * environment, and never shows the key.
     *
     * @param property the value of {@code jdk.httpclient.HttpClient.log}, empty when it is not set
     * @return the warning, or empty when headers are not logged
     */
    public static java.util.Optional<String> headerLogWarning(String property) {
        var value = property.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("headers") || value.contains("all")) {
            return java.util.Optional.of("jdk.httpclient.HttpClient.log asks the JDK to log headers, which would write"
                    + " the Authorization header of every Jev request to its log; remove headers and all from it");
        }
        return java.util.Optional.empty();
    }

    @Override
    public Preparation prepare(DecisionRequest request) {
        Objects.requireNonNull(request, "request");
        return preparer.apply(request);
    }

    @Override
    public CompletableFuture<DecisionResult> decide(PreparedRequest prepared) {
        Objects.requireNonNull(prepared, "prepared");
        var exchange = new AbortableExchange();
        var result = calls.<DecisionResult>open(exchange, FailureClassifier.failed(FailureKind.NETWORK, CLOSED),
                FailureClassifier.failed(FailureKind.TIMEOUT, "no response within the request timeout"));
        if (!result.isDone()) {
            calls.submit(result, FailureClassifier.queueFull(), () -> sendDecision(prepared, exchange, result),
                    classifier::fromThrowable);
        }
        return result;
    }

    /**
     * Checks the service with {@code GET /v1/models}. The check is the call that opens the connection decisions reuse,
     * so it may spend the connection timeout and then the request timeout: a slow handshake from a far host is not cut
     * at the deadline of a decision, which would fail every reconnection the same way.
     *
     * @return the state the check found
     */
    @Override
    public CompletableFuture<ServiceCheck> check() {
        var exchange = new AbortableExchange();
        var result = calls.open(exchange, new ServiceCheck(ServiceState.PAUSED, CLOSED),
                new ServiceCheck(ServiceState.PAUSED, "no response within the request timeout"),
                checkTimeout().plus(TIMEOUT_MARGIN).toMillis());
        if (!result.isDone()) {
            calls.submit(result, ModelCatalogParser.interpret(FailureClassifier.queueFull()),
                    () -> sendCheck(exchange, result), failure -> ModelCatalogParser.interpret(classifier.fromThrowable(failure)));
        }
        return result;
    }

    /**
     * Completes every pending future with a {@code client closed} failure, stops the executor, the HTTP client and the
     * scenario recorder, and waits briefly for their threads to end: at most 0.5 s for the executor, 0.5 s for the HTTP
     * client and, with scenario recording on, 1 s for pending scenario writes, so a shutdown can take about 2 s. Later
     * calls answer at once. Idempotent.
     */
    @Override
    public void close() {
        if (!calls.close()) {
            return;
        }
        executor.shutdownNow();
        http.shutdownNow();
        executor.awaitTermination();
        try {
            http.awaitTermination(SystemOneExecutor.CLOSE_WAIT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        recorder.close();
    }

    boolean executorTerminated() {
        return executor.terminated();
    }

    private CompletableFuture<?> sendDecision(PreparedRequest prepared, AbortableExchange exchange,
                                              CompletableFuture<DecisionResult> result) {
        if (result.isDone()) {
            return CompletableFuture.completedFuture(null);
        }
        return exchange.send(transport, http, post(prepared.body()), settings.maxResponseBytes())
                .thenApplyAsync(response -> decision(response, prepared), executor.parsing())
                .whenComplete((value, failure) -> complete(result, failure == null ? value
                        : classifier.fromThrowable(failure), prepared));
    }

    /**
     * Completes the caller's future. An answer is queued for the scenario bank first, so a close in the same turn still
     * writes it, and dropped again if the deadline completed the future in between.
     */
    private void complete(CompletableFuture<DecisionResult> result, DecisionResult outcome, PreparedRequest prepared) {
        if (result.isDone() || !(outcome instanceof DecisionResult.Answered answered)) {
            result.complete(outcome);
            return;
        }
        var queued = recorder.record(prepared.body(), prepared.sent(), answered);
        if (!result.complete(outcome)) {
            queued.run();
        }
    }

    private CompletableFuture<?> sendCheck(AbortableExchange exchange, CompletableFuture<ServiceCheck> result) {
        if (result.isDone()) {
            return CompletableFuture.completedFuture(null);
        }
        return exchange.send(transport, http, request("/v1/models").timeout(checkTimeout()).GET().build(),
                settings.maxResponseBytes())
                .thenApplyAsync(this::serviceCheck, executor.parsing())
                .whenComplete((value, failure) -> result.complete(failure == null ? value
                        : ModelCatalogParser.interpret(classifier.fromThrowable(failure))));
    }

    private DecisionResult decision(HttpResponse<String> response, PreparedRequest prepared) {
        if (response.statusCode() != 200) {
            return classifier.fromStatus(response.statusCode(), response.headers(), response.body());
        }
        var started = timings.now();
        var answered = ResponseParser.parse(response.body(), prepared.sent().questions(), prepared.sent().fitChecks(),
                prepared.sent().facts().brief());
        timings.record(timings.now() - started, prepared.bodyChars());
        return answered;
    }

    private ServiceCheck serviceCheck(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return ModelCatalogParser.interpret(response.statusCode(),
                    classifier.fromStatus(response.statusCode(), response.headers(), response.body()));
        }
        return ModelCatalogParser.interpret(response.body(), settings.model());
    }

    private Duration checkTimeout() {
        return settings.connectTimeout().plus(settings.requestTimeout());
    }

    private HttpRequest post(String body) {
        return request("/v1/systemone").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpRequest.Builder request(String path) {
        var builder = HttpRequest.newBuilder(settings.endpoint(path)).timeout(settings.requestTimeout())
                .header("Accept", "application/json").header("User-Agent", userAgent);
        if (settings.sendAuthorization()) {
            builder.header("Authorization", authorization);
        }
        return builder;
    }
}
