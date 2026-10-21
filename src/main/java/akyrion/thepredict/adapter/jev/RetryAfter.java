package akyrion.thepredict.adapter.jev;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses a {@code retry-after} header value (M08.5): whole seconds or an HTTP date. The result is bounded to
 * {@code [0 s, 300 s]}. Converting an HTTP date needs the wall clock, the only wall-clock use of the adapter
 * (documented exception to rule C6); the caller passes it in.
 */
final class RetryAfter {
    /** Longest pause the service may ask for. */
    static final Duration MAX = Duration.ofSeconds(300);

    private static final Pattern SECONDS = Pattern.compile("-?[0-9]{1,30}");

    private RetryAfter() {
    }

    /**
     * Parses a header value.
     *
     * @param value header value
     * @param now current wall-clock time, used only for HTTP dates
     * @return the bounded delay, or empty when the value is neither seconds nor an HTTP date
     */
    static Optional<Duration> parse(String value, Instant now) {
        var trimmed = value.trim();
        if (SECONDS.matcher(trimmed).matches()) {
            return Optional.of(bounded(seconds(trimmed)));
        }
        try {
            var date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Optional.of(bounded(Duration.between(now, date)));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    /** Whole seconds; leading zeros do not count toward the size of the number (RFC 9110 allows {@code 1*DIGIT}). */
    private static Duration seconds(String digits) {
        if (digits.startsWith("-")) {
            return Duration.ZERO;
        }
        var first = 0;
        while (first < digits.length() - 1 && digits.charAt(first) == '0') {
            first++;
        }
        var significant = digits.substring(first);
        return significant.length() > 6 ? MAX : Duration.ofSeconds(Long.parseLong(significant));
    }

    private static Duration bounded(Duration delay) {
        if (delay.isNegative()) {
            return Duration.ZERO;
        }
        return delay.compareTo(MAX) > 0 ? MAX : delay;
    }
}
