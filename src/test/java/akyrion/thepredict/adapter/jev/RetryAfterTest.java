package akyrion.thepredict.adapter.jev;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryAfterTest {
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    void secondsAreReadAsTheyCome() {
        assertEquals(Optional.of(Duration.ofSeconds(2)), RetryAfter.parse("2", NOW));
        assertEquals(Optional.of(Duration.ofSeconds(7)), RetryAfter.parse(" 7 ", NOW));
        assertEquals(Optional.of(Duration.ZERO), RetryAfter.parse("0", NOW));
        assertEquals(Optional.of(Duration.ofSeconds(300)), RetryAfter.parse("300", NOW));
    }

    @Test
    void anHttpDateBecomesTheDelayFromNow() {
        assertEquals(Optional.of(Duration.ofSeconds(45)), RetryAfter.parse("Tue, 29 Sep 2026 10:00:45 GMT", NOW));
        assertEquals(Optional.of(Duration.ZERO), RetryAfter.parse("Tue, 29 Sep 2026 09:59:00 GMT", NOW));
        assertEquals(Optional.of(RetryAfter.MAX), RetryAfter.parse("Wed, 30 Sep 2026 10:00:00 GMT", NOW));
    }

    @Test
    void negativeValuesBecomeZero() {
        assertEquals(Optional.of(Duration.ZERO), RetryAfter.parse("-5", NOW));
    }

    @Test
    void hugeValuesAreBoundedToFiveMinutes() {
        assertEquals(Optional.of(RetryAfter.MAX), RetryAfter.parse("301", NOW));
        assertEquals(Optional.of(RetryAfter.MAX), RetryAfter.parse("99999999999999999999999", NOW));
        assertEquals(Optional.of(RetryAfter.MAX), RetryAfter.parse("0000001234567", NOW));
    }

    @Test
    void leadingZerosAreTheSameSeconds() {
        assertEquals(Optional.of(Duration.ofSeconds(5)), RetryAfter.parse("0000005", NOW));
        assertEquals(Optional.of(Duration.ZERO), RetryAfter.parse("00000000000", NOW));
        assertEquals(Optional.of(Duration.ofSeconds(120)), RetryAfter.parse("000000000120", NOW));
    }

    @Test
    void invalidTextIsIgnored() {
        assertEquals(Optional.empty(), RetryAfter.parse("soon", NOW));
        assertEquals(Optional.empty(), RetryAfter.parse("", NOW));
        assertEquals(Optional.empty(), RetryAfter.parse("1.5", NOW));
    }
}
