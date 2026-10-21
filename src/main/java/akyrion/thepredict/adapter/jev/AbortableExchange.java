package akyrion.thepredict.adapter.jev;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One HTTP exchange that the client can abort from any thread. Aborting cancels the JDK transport future, which
 * the JDK propagates to the exchange; a cancellation that arrives
 * before the request starts cancels it as soon as it starts. Thread-safe.
 */
final class AbortableExchange {
    private final AtomicReference<CompletableFuture<?>> exchange = new AtomicReference<>();
    private volatile boolean aborted;

    CompletableFuture<HttpResponse<String>> send(Transport transport, HttpClient client, HttpRequest request,
                                                 int maxBytes) {
        var future = transport.send(client, request, BoundedBody.handler(maxBytes));
        exchange.set(future);
        if (aborted) {
            future.cancel(true);
        }
        return future;
    }

    /** The single call that reaches the network; {@code HttpClient::sendAsync} outside tests. */
    @FunctionalInterface
    interface Transport {
        CompletableFuture<HttpResponse<String>> send(HttpClient client, HttpRequest request,
                                                     HttpResponse.BodyHandler<String> handler);
    }

    void abort() {
        aborted = true;
        var pending = exchange.get();
        if (pending != null) {
            pending.cancel(true);
        }
    }
}
