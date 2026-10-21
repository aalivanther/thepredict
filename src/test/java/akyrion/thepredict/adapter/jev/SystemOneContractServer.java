package akyrion.thepredict.adapter.jev;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Local System One contract server for tests (M08.8). Binds only to the loopback address on an ephemeral
 * port, so no test reaches the network. Each scenario is a {@link Reply}; every request is recorded with its
 * headers and body. Stalled replies wait for {@link #close()}, never for a fixed time.
 */
final class SystemOneContractServer implements AutoCloseable {
    /** How a context answers one request. */
    @FunctionalInterface
    interface Reply {
        void send(HttpExchange exchange, Received request, CountDownLatch released) throws IOException;
    }

    /** One recorded request. */
    record Received(String method, String path, Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst()).findFirst().orElse("");
        }
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Semaphore arrivals = new Semaphore(0);
    private final CountDownLatch released = new CountDownLatch(1);
    private volatile Reply decide = json(200, fixture("response-valid.json"));
    private volatile Reply models = json(200, fixture("models.json"));

    private SystemOneContractServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/systemone", exchange -> handle(exchange, decide));
        server.createContext("/v1/models", exchange -> handle(exchange, models));
        server.setExecutor(executor);
        server.start();
    }

    static SystemOneContractServer start() {
        try {
            return new SystemOneContractServer();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    void onDecide(Reply reply) {
        decide = Objects.requireNonNull(reply);
    }

    void onModels(Reply reply) {
        models = Objects.requireNonNull(reply);
    }

    List<Received> received() {
        return List.copyOf(received);
    }

    long posts() {
        return received.stream().filter(request -> request.method().equals("POST")).count();
    }

    void awaitArrival() throws InterruptedException {
        if (!arrivals.tryAcquire(10, TimeUnit.SECONDS)) {
            throw new AssertionError("no request reached the contract server");
        }
    }

    @Override
    public void close() {
        released.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    static Reply json(int status, String body) {
        return json(status, body, Map.of());
    }

    static Reply json(int status, String body, Map<String, String> headers) {
        return (exchange, request, released) -> {
            headers.forEach(exchange.getResponseHeaders()::add);
            write(exchange, status, body.getBytes(StandardCharsets.UTF_8));
        };
    }

    static Reply echoingAuthorization(int status) {
        return (exchange, request, released) -> write(exchange, status, ("{\"detail\":\"bad key "
                + request.header("Authorization") + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    static Reply stallBeforeHeaders() {
        return (exchange, request, released) -> {
            awaitQuietly(released);
            exchange.close();
        };
    }

    static Reply stallAfterHeaders() {
        return (exchange, request, released) -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 1000);
            exchange.getResponseBody().write("{\"model\":".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            awaitQuietly(released);
            exchange.close();
        };
    }

    static Reply cut() {
        return (exchange, request, released) -> exchange.close();
    }

    static Reply chunked(int bytes) {
        return (exchange, request, released) -> {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                var block = new byte[1024];
                Arrays.fill(block, (byte) 'x');
                for (var sent = 0; sent < bytes; sent += block.length) {
                    out.write(block, 0, Math.min(block.length, bytes - sent));
                }
            }
        };
    }

    static String fixture(String name) {
        try (InputStream stream = SystemOneContractServer.class.getResourceAsStream("/systemone/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void handle(HttpExchange exchange, Reply reply) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var request = new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                Map.copyOf(exchange.getRequestHeaders()), body);
        received.add(request);
        arrivals.release();
        try {
            reply.send(exchange, request, released);
        } catch (IOException ignored) {
            exchange.close();
        }
    }

    private static void write(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
