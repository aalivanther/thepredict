package akyrion.thepredict.adapter.jev;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Non-blocking body subscriber that keeps at most {@code maxBytes} of a response (M08.1). A larger body cancels
 * the subscription as soon as the limit is crossed, so it is never loaded whole and no executor thread waits on
 * a stalled stream. For a {@code 200} a larger body fails with {@code response body exceeds maxResponseBytes};
 * for any other status the body only feeds error classification, so a larger one is discarded as empty. A
 * malformed {@code Content-Length} is rejected the same way, and a body that is not valid UTF-8 fails (a {@code 200})
 * or is discarded (any other status).
 * The JDK calls the subscriber methods serially.
 */
final class BoundedBody implements HttpResponse.BodySubscriber<String> {
    /** Message of the failure for an oversized success body. */
    static final String TOO_LARGE = "response body exceeds maxResponseBytes";
    /** Message of the failure for a success body that is not valid UTF-8. */
    static final String NOT_UTF8 = "response body is not valid UTF-8";
    /** Message of the failure for a success response whose {@code Content-Length} is not a number. */
    static final String BAD_LENGTH = "response has an invalid Content-Length";

    private final int maxBytes;
    private final boolean strict;
    private final String upfrontFailure;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<String> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    BoundedBody(int maxBytes, boolean strict, String upfrontFailure) {
        this.maxBytes = maxBytes;
        this.strict = strict;
        this.upfrontFailure = upfrontFailure;
    }

    /**
     * Returns a handler that bounds every body to {@code maxBytes}. A {@code Content-Length} above the limit is
     * rejected before any byte is read.
     *
     * @param maxBytes largest accepted body in bytes
     * @return the body handler
     */
    static HttpResponse.BodyHandler<String> handler(int maxBytes) {
        return info -> new BoundedBody(maxBytes, info.statusCode() == 200, upfrontFailure(info.headers(), maxBytes));
    }

    private static String upfrontFailure(HttpHeaders headers, int maxBytes) {
        try {
            return headers.firstValueAsLong("content-length").orElse(0L) > maxBytes ? TOO_LARGE : "";
        } catch (NumberFormatException exception) {
            return BAD_LENGTH;
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (!upfrontFailure.isEmpty()) {
            reject(upfrontFailure);
            return;
        }
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) {
            return;
        }
        for (var buffer : buffers) {
            if ((long) bytes.size() + buffer.remaining() > maxBytes) {
                overflow();
                return;
            }
            var chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.writeBytes(chunk);
        }
    }

    @Override
    public void onError(Throwable failure) {
        body.completeExceptionally(failure);
    }

    @Override
    public void onComplete() {
        try {
            body.complete(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString());
        } catch (CharacterCodingException exception) {
            if (strict) {
                body.completeExceptionally(new SystemOneProtocolException(NOT_UTF8));
            } else {
                body.complete("");
            }
        }
    }

    @Override
    public CompletionStage<String> getBody() {
        return body;
    }

    private void overflow() {
        reject(TOO_LARGE);
    }

    private void reject(String rule) {
        subscription.cancel();
        bytes.reset();
        if (strict) {
            body.completeExceptionally(new SystemOneProtocolException(rule));
        } else {
            body.complete("");
        }
    }
}
