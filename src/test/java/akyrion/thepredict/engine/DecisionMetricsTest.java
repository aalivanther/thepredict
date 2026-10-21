package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.Movement;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionMetricsTest {
    private static final long MICRO = 1_000L;
    private static final long MINUTE = 60_000_000_000L;

    private final DecisionMetrics metrics = new DecisionMetrics(() -> 0L);

    @Test
    void percentilesAreReadByCumulativeCountOverFixedBuckets() {
        for (var index = 0; index < 90; index++) {
            metrics.record(DecisionMetrics.Span.ROUNDTRIP, 80 * MICRO * 1000, 0);
        }
        for (var index = 0; index < 9; index++) {
            metrics.record(DecisionMetrics.Span.ROUNDTRIP, 400 * MICRO * 1000, 0);
        }
        metrics.record(DecisionMetrics.Span.ROUNDTRIP, 3_000 * MICRO * 1000, 0);
        assertEquals(OptionalLong.of(100_000), metrics.percentileMicros(DecisionMetrics.Span.ROUNDTRIP, 0.50, 0));
        assertEquals(OptionalLong.of(500_000), metrics.percentileMicros(DecisionMetrics.Span.ROUNDTRIP, 0.95, 0));
        assertEquals(OptionalLong.of(500_000), metrics.percentileMicros(DecisionMetrics.Span.ROUNDTRIP, 0.99, 0));
        assertEquals(OptionalLong.of(Long.MAX_VALUE), metrics.percentileMicros(DecisionMetrics.Span.ROUNDTRIP, 1.0, 0));
        assertEquals(100, metrics.samples(DecisionMetrics.Span.ROUNDTRIP));
        assertEquals(3_000_000, metrics.maxMicros(DecisionMetrics.Span.ROUNDTRIP));
        assertEquals(OptionalLong.empty(), metrics.percentileMicros(DecisionMetrics.Span.CAPTURE, 0.5, 0));
    }

    @Test
    void theBucketBoundsAreInclusiveAndNegativeDurationsCountAsZero() {
        metrics.record(DecisionMetrics.Span.CAPTURE, 50 * MICRO, 0);
        assertEquals(OptionalLong.of(50), metrics.percentileMicros(DecisionMetrics.Span.CAPTURE, 1.0, 0));
        metrics.record(DecisionMetrics.Span.APPLY, -5, 0);
        assertEquals(OptionalLong.of(50), metrics.percentileMicros(DecisionMetrics.Span.APPLY, 1.0, 0));
        metrics.record(DecisionMetrics.Span.DISPATCH, 51 * MICRO, 0);
        assertEquals(OptionalLong.of(100), metrics.percentileMicros(DecisionMetrics.Span.DISPATCH, 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> metrics.percentileMicros(DecisionMetrics.Span.CAPTURE, 0, 0));
    }

    @Test
    void theWindowKeepsTheCurrentAndThePreviousMinuteOnly() {
        metrics.record(DecisionMetrics.Span.INBOX_WAIT, 2_000 * MICRO, 0);
        metrics.record(DecisionMetrics.Span.INBOX_WAIT, 40 * MICRO, MINUTE);
        assertEquals(OptionalLong.of(2_000), metrics.percentileMicros(DecisionMetrics.Span.INBOX_WAIT, 1.0, MINUTE));
        assertEquals(OptionalLong.of(50), metrics.percentileMicros(DecisionMetrics.Span.INBOX_WAIT, 1.0, 2 * MINUTE));
        assertEquals(OptionalLong.empty(), metrics.percentileMicros(DecisionMetrics.Span.INBOX_WAIT, 1.0, 4 * MINUTE));
        assertEquals(2, metrics.samples(DecisionMetrics.Span.INBOX_WAIT));
        assertEquals(2_000, metrics.maxMicros(DecisionMetrics.Span.INBOX_WAIT));
    }

    @Test
    void countersPerCauseCoverTheFailuresOutcomesAndTheCausesOfM10() {
        metrics.increment(FailureKind.RATE_LIMITED);
        metrics.increment(FailureKind.RATE_LIMITED);
        metrics.increment(DecisionDispatcher.Outcome.NO_BUDGET);
        metrics.increment(FallbackReason.SERVICE_PAUSED);
        metrics.increment(ExecutionOutcome.CAST);
        metrics.increment(DiscardReason.STALE_SEQUENCE);
        metrics.add(DecisionMetrics.Counter.INPUT_TOKENS, 420);
        metrics.add(DecisionMetrics.Counter.INPUT_TOKENS, 80);
        assertEquals(2, metrics.count(FailureKind.RATE_LIMITED));
        assertEquals(0, metrics.count(FailureKind.TIMEOUT));
        assertEquals(1, metrics.count(DecisionDispatcher.Outcome.NO_BUDGET));
        assertEquals(1, metrics.count(FallbackReason.SERVICE_PAUSED));
        assertEquals(1, metrics.count(ExecutionOutcome.CAST));
        assertEquals(1, metrics.count(DiscardReason.STALE_SEQUENCE));
        assertEquals(500, metrics.total(DecisionMetrics.Counter.INPUT_TOKENS));
        assertThrows(IllegalArgumentException.class, () -> metrics.increment(Movement.HOLD));
        assertThrows(IllegalArgumentException.class, () -> metrics.add(DecisionMetrics.Counter.ANSWERED, -1));
    }
}
