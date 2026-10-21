package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.UncheckedIOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Translates HTTP statuses and exceptions into {@link DecisionResult.Failed} (M08.5). A {@code detail} carries
 * the status and the affected rule or field, never headers, the key or response text: for a 422 only the
 * {@code loc} paths of {@code HTTPValidationError}, filtered to plain field names, are read; for a 400 only whether
 * {@code detail} is an {@code api_usage_error} about an unknown model, which the real service answers with HTTP 400
 * (live check of M13, 2026-09-29). The model name in that message is configuration and is never echoed. Thread-safe.
 */
final class FailureClassifier {
    /** Detail of a future cancelled by its caller; M10.1 discards it without counting a service failure. */
    static final String CANCELLED = "cancelled";
    /** Detail when the bounded executor of the client rejects work. */
    static final String QUEUE_FULL = "client queue is full";
    /** Detail of an HTTP 400 for an unknown model; it never contains the configured model. */
    static final String UNKNOWN_MODEL = "HTTP 400: the service does not know the configured model";

    private static final Pattern FIELD = Pattern.compile("[A-Za-z0-9_]{1,40}");
    private static final int MAX_LOC_PARTS = 6;

    private final Clock wallClock;

    FailureClassifier(Clock wallClock) {
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
    }

    /**
     * Classifies a non-success status.
     *
     * @param status HTTP status
     * @param headers response headers, read only for {@code retry-after}
     * @param body bounded response body, read only for a 400 or a 422; may be empty
     * @return the typed failure
     */
    DecisionResult.Failed fromStatus(int status, HttpHeaders headers, String body) {
        return switch (status) {
            case 400 -> unknownModel(body) ? failed(FailureKind.MODEL_UNAVAILABLE, UNKNOWN_MODEL)
                    : failed(FailureKind.INVALID_REQUEST, "HTTP 400: the service rejected the request");
            case 401, 403 -> failed(FailureKind.AUTHENTICATION, "HTTP " + status + ": the service rejected the credentials");
            case 422 -> validation(body);
            case 429 -> new DecisionResult.Failed(FailureKind.RATE_LIMITED, retryAfter(headers), "HTTP 429: rate limited");
            case 529 -> new DecisionResult.Failed(FailureKind.OVERLOADED, retryAfter(headers), "HTTP 529: service overloaded");
            default -> failed(FailureKind.SERVICE_ERROR, "HTTP " + status + (status >= 500 && status <= 599
                    ? ": service error" : ": unexpected status"));
        };
    }

    /**
     * Classifies an exception of the transport, the encoder or the parser.
     *
     * @param failure the exception, possibly wrapped in a {@code CompletionException}
     * @return the typed failure
     */
    DecisionResult.Failed fromThrowable(Throwable failure) {
        var cause = unwrap(failure);
        return switch (cause) {
            case SystemOneProtocolException protocol -> failed(FailureKind.INVALID_RESPONSE, protocol.getMessage());
            case SystemOneRequestException request -> failed(FailureKind.INVALID_REQUEST, request.getMessage());
            case HttpTimeoutException timeout -> failed(FailureKind.TIMEOUT, "no response within the request timeout");
            case TimeoutException timeout -> failed(FailureKind.TIMEOUT, "no response within the request timeout");
            case CancellationException cancelled -> failed(FailureKind.NETWORK, CANCELLED);
            case RejectedExecutionException rejected -> queueFull();
            default -> failed(FailureKind.NETWORK, "network failure (" + cause.getClass().getSimpleName() + ")");
        };
    }

    static DecisionResult.Failed queueFull() {
        return failed(FailureKind.NETWORK, QUEUE_FULL);
    }

    static DecisionResult.Failed failed(FailureKind kind, String detail) {
        return new DecisionResult.Failed(kind, Optional.empty(), detail);
    }

    private DecisionResult.Failed validation(String body) {
        var locations = validationLocations(body);
        for (var loc : locations) {
            if (loc.size() == 2 && isText(loc.get(0), "body") && isText(loc.get(1), "model")) {
                return failed(FailureKind.MODEL_UNAVAILABLE, "HTTP 422: the service rejected the model");
            }
        }
        for (var loc : locations) {
            var fields = fields(loc);
            if (!fields.isEmpty()) {
                return failed(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request at "
                        + String.join(".", fields));
            }
        }
        return failed(FailureKind.INVALID_REQUEST, "HTTP 422: the service rejected the request");
    }

    /**
     * Tells whether a 400 body is {@code {"detail": {"error_type": "api_usage_error", "message": "Unknown model…"}}},
     * read strictly; any other shape, or a body that is not JSON, is not.
     */
    private static boolean unknownModel(String body) {
        try {
            if (StrictJson.parseObject(body, "usage error").get("detail") instanceof JsonObject detail
                    && detail.get("error_type") instanceof JsonElement type && isText(type, "api_usage_error")
                    && detail.get("message") instanceof JsonElement message && message.isJsonPrimitive()
                    && message.getAsJsonPrimitive().isString()) {
                return message.getAsString().startsWith("Unknown model");
            }
            return false;
        } catch (SystemOneProtocolException exception) {
            return false;
        }
    }

    private static List<JsonArray> validationLocations(String body) {
        var locations = new ArrayList<JsonArray>();
        try {
            var detail = StrictJson.parseObject(body, "validation error").get("detail");
            if (detail instanceof JsonArray entries) {
                for (var entry : entries) {
                    if (entry.isJsonObject() && entry.getAsJsonObject().get("loc") instanceof JsonArray loc) {
                        locations.add(loc);
                    }
                }
            }
        } catch (SystemOneProtocolException exception) {
            return List.of();
        }
        return locations;
    }

    private static List<String> fields(JsonArray loc) {
        var parts = new ArrayList<String>();
        for (JsonElement part : loc) {
            if (parts.size() < MAX_LOC_PARTS && part.isJsonPrimitive() && FIELD.matcher(part.getAsString()).matches()) {
                parts.add(part.getAsString());
            }
        }
        return parts;
    }

    private static boolean isText(JsonElement element, String text) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().equals(text);
    }

    private Optional<Duration> retryAfter(HttpHeaders headers) {
        return headers.firstValue("retry-after").flatMap(value -> RetryAfter.parse(value, wallClock.instant()));
    }

    private static Throwable unwrap(Throwable failure) {
        var cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException
                || cause instanceof UncheckedIOException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
