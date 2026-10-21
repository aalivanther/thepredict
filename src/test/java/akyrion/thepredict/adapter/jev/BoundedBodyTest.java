package akyrion.thepredict.adapter.jev;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedBodyTest {
    @Test
    void aBodyExactlyAtTheLimitIsRead() {
        var subscription = new RecordingSubscription();
        var body = subscribe(200, Map.of(), 64, subscription);
        body.onNext(List.of(bytes("x".repeat(40)), bytes("y".repeat(24))));
        body.onComplete();
        assertEquals("x".repeat(40) + "y".repeat(24), body.getBody().toCompletableFuture().join());
        assertEquals(Long.MAX_VALUE, subscription.requested);
        assertFalse(subscription.cancelled);
    }

    @Test
    void oneByteOverTheLimitCancelsAndFailsASuccessBody() {
        var subscription = new RecordingSubscription();
        var body = subscribe(200, Map.of(), 64, subscription);
        body.onNext(List.of(bytes("x".repeat(64))));
        body.onNext(List.of(bytes("z")));
        assertTrue(subscription.cancelled);
        var failure = assertThrows(CompletionException.class, () -> body.getBody().toCompletableFuture().join());
        assertInstanceOf(SystemOneProtocolException.class, failure.getCause());
        assertEquals(BoundedBody.TOO_LARGE, failure.getCause().getMessage());
    }

    @Test
    void anEndlessBodyIsNeverKeptBeyondTheLimit() {
        var subscription = new RecordingSubscription();
        var body = subscribe(200, Map.of(), 4096, subscription);
        var buffers = new ArrayList<ByteBuffer>();
        for (var index = 0; index < 1000; index++) {
            var buffer = bytes("x".repeat(1024));
            buffers.add(buffer);
            body.onNext(List.of(buffer));
        }
        var consumed = buffers.stream().filter(buffer -> !buffer.hasRemaining()).count();
        assertEquals(4, consumed, "only the buffers within the limit are copied");
        assertTrue(subscription.cancelled);
    }

    @Test
    void aContentLengthOverTheLimitIsRejectedBeforeRequestingAnyByte() {
        var subscription = new RecordingSubscription();
        var body = subscribe(200, Map.of("content-length", List.of("5000")), 1024, subscription);
        assertTrue(subscription.cancelled);
        assertEquals(0, subscription.requested);
        assertThrows(CompletionException.class, () -> body.getBody().toCompletableFuture().join());
    }

    @Test
    void anOversizedErrorBodyIsDiscardedSoTheStatusStillClassifies() {
        var subscription = new RecordingSubscription();
        var body = subscribe(500, Map.of(), 16, subscription);
        body.onNext(List.of(bytes("e".repeat(17))));
        assertTrue(subscription.cancelled);
        assertEquals("", body.getBody().toCompletableFuture().join());
    }

    @Test
    void aSuccessBodyThatIsNotUtf8IsAProtocolFailure() {
        var body = subscribe(200, Map.of(), 64, new RecordingSubscription());
        body.onNext(List.of(ByteBuffer.wrap(new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'})));
        body.onComplete();
        var failure = assertThrows(CompletionException.class, () -> body.getBody().toCompletableFuture().join());
        assertInstanceOf(SystemOneProtocolException.class, failure.getCause());
        assertEquals(BoundedBody.NOT_UTF8, failure.getCause().getMessage());
    }

    @Test
    void anErrorBodyThatIsNotUtf8IsDiscarded() {
        var body = subscribe(422, Map.of(), 64, new RecordingSubscription());
        body.onNext(List.of(ByteBuffer.wrap(new byte[] {(byte) 0xFF})));
        body.onComplete();
        assertEquals("", body.getBody().toCompletableFuture().join());
    }

    @Test
    void aMalformedContentLengthIsAProtocolFailure() {
        var subscription = new RecordingSubscription();
        var body = subscribe(200, Map.of("content-length", List.of("12abc")), 64, subscription);
        assertTrue(subscription.cancelled);
        var failure = assertThrows(CompletionException.class, () -> body.getBody().toCompletableFuture().join());
        assertEquals(BoundedBody.BAD_LENGTH, failure.getCause().getMessage());
    }

    @Test
    void aTransportErrorIsPassedOn() {
        var body = subscribe(200, Map.of(), 16, new RecordingSubscription());
        body.onError(new IOException("reset"));
        var failure = assertThrows(CompletionException.class, () -> body.getBody().toCompletableFuture().join());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    private static HttpResponse.BodySubscriber<String> subscribe(int status, Map<String, List<String>> headers,
                                                                 int maxBytes, Flow.Subscription subscription) {
        var info = new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
        var body = BoundedBody.handler(maxBytes).apply(info);
        body.onSubscribe(subscription);
        return body;
    }

    private static ByteBuffer bytes(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private long requested;
        private boolean cancelled;

        @Override
        public void request(long count) {
            requested += count;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
