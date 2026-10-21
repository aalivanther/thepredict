package akyrion.thepredict.adapter.jev;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * One POST against {@link SystemOneContractServer} (M22.4). The server binds the loopback only.
 */
public final class LoopbackProbe {
    private LoopbackProbe() {
    }

    /**
     * Ticks a harness charges for a scripted latency, {@code ceil(milliseconds / 50)}.
     *
     * @param latencyMillis scripted latency
     * @return the tick step
     */
    public static int ticks(int latencyMillis) {
        return (latencyMillis + 49) / 50;
    }

    /**
     * Posts once and waits the scripted delay inside the loopback server.
     *
     * @param delayMillis delay before the response
     * @return posts the server recorded
     * @throws Exception when the exchange fails
     */
    public static int posts(int delayMillis) throws Exception {
        try (var server = SystemOneContractServer.start(); var client = HttpClient.newHttpClient()) {
            server.onDecide((exchange, request, released) -> {
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted");
                    }
                }
                var body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            var request = HttpRequest.newBuilder(URI.create(server.baseUrl() + "/v1/systemone"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("status " + response.statusCode());
            }
            return (int) server.posts();
        }
    }
}
