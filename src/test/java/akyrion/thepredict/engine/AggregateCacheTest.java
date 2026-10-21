package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateCacheTest {
    @Test
    void aHundredBatchesStayExactWhileTheRingHoldsAtMostTwo() {
        var inbox = new Inbox<InboxEntry>(8);
        var store = accepting();
        var metrics = new StorageMetrics();
        var cache = new AggregateCache(store, metrics, 4096, 1, 4, inbox);
        var key = key("guardian");
        var expected = OutcomeStat.EMPTY;
        var seen = 0;
        for (var index = 0; index < 100; index++) {
            var effect = 0.01 * index;
            expected = OutcomeStatMath.merge(expected, OutcomeStatMath.add(OutcomeStat.EMPTY, effect, 0, 0, false,
                    false, false, 0, 0));
            cache.outcomeClosed("guardian", summary(effect), index);
            var sent = cache.flush(index, AggregateCache.FLUSH_KEYS);
            assertTrue(sent.isPresent());
            seen = Math.max(seen, cache.inFlight());
            inbox.offerStorage(new StorageReply.BatchApplied(sent.get().batchId(), index));
            cache.drain(4);
            assertTrue(cache.inFlight() <= 2);
        }
        assertTrue(seen <= 2);
        var loaded = cache.outcome(key).orElseThrow();
        assertEquals(100, loaded.windows());
        assertEquals(expected.effectMean(), loaded.effectMean(), 1e-9);
    }

    @Test
    void batchFiftyFailsTheRingRefusesTheNinthAndAFullPendingDropsAKey() {
        var inbox = new Inbox<InboxEntry>(8);
        var metrics = new StorageMetrics();
        var cache = new AggregateCache(accepting(), metrics, 4096, 100, 4, inbox);
        for (var index = 0; index < 8; index++) {
            cache.outcomeClosed("guardian", summary(index), index);
            assertTrue(cache.flush(0, 1024).isPresent());
        }
        cache.outcomeClosed("guardian", summary(1), 8);
        assertTrue(cache.flush(0, 1024).isEmpty());
        assertTrue(metrics.total("AGGREGATE_RING_FULL") >= 1);
        assertFalse(cache.degraded());
        cache.flush(AggregateCache.DEGRADED_AFTER_NANOS, 1024);
        assertTrue(cache.degraded());
        inbox.offerStorage(new StorageReply.BatchFailed(50, "boom", 1));
        cache.drain(4);
        assertEquals(1, metrics.total("AGGREGATE_REPLY_UNKNOWN"));
        inbox.offerStorage(new StorageReply.BatchFailed(1, "boom", 1));
        cache.drain(4);
        assertEquals(1, metrics.total("AGGREGATE_BATCH_FAILED"));
        var dropped = new StorageMetrics();
        var rejecting = new AggregateCache(rejecting(), dropped, 1, 100, 4, new Inbox<>(4));
        rejecting.outcomeClosed("guardian", summary(1), 1);
        assertTrue(rejecting.flush(0, 1024).isEmpty());
        assertTrue(rejecting.outcome(key("guardian")).isPresent());
        rejecting.outcomeClosed("other", summary(1), 1);
        assertEquals(1, dropped.total("AGGREGATE_KEY_DROPPED"));
        assertEquals(1, rejecting.outcome(key("guardian")).orElseThrow().windows());
    }

    @Test
    void aLoadClearsOlderBatchesAnUnknownIdIsCountedAndOffDiscards() {
        var inbox = new Inbox<InboxEntry>(8);
        var metrics = new StorageMetrics();
        var cache = new AggregateCache(accepting(), metrics, 4096, 100, 4, inbox);
        assertTrue(cache.warming());
        cache.outcomeClosed("guardian", summary(1), 1);
        var sent = cache.flush(0, 1024).orElseThrow();
        inbox.offerStorage(new StorageReply.OutcomeStatsLoaded(List.of(new StorageReply.LoadedRow(key("guardian"),
                OutcomeStatMath.add(OutcomeStat.EMPTY, 1, 0, 0, false, false, false, 0, 0))), sent.batchId(), false, 1));
        cache.drain(4);
        assertFalse(cache.warming());
        assertEquals(0, cache.inFlight());
        inbox.offerStorage(new StorageReply.BatchApplied(99, 1));
        cache.drain(4);
        assertEquals(1, metrics.total("AGGREGATE_REPLY_UNKNOWN"));
        var off = AggregateCache.off(new StorageMetrics());
        off.outcomeClosed("guardian", summary(1), 1);
        off.onTick(100, 0);
        assertTrue(off.outcome(key("guardian")).isEmpty());
    }

    private static AggregateStore accepting() {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                return true;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return true;
            }
        };
    }

    private static AggregateStore rejecting() {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                return false;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return false;
            }
        };
    }

    private static OutcomeStatKey key(String profile) {
        return new OutcomeStatKey(profile, new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                "v1:NONE:NONE");
    }

    private static OutcomeWindow.Summary summary(double effect) {
        return new OutcomeWindow.Summary(ControlLevel.JEV, new TacticId("keep_distance"), Movement.APPROACH, 0, 1,
                0, 0, false, 0, 0, false, false, effect);
    }

    @Test
    void aDisabledStoreDropsEveryRowListOnTick() {
        var state = new StorageState[] {StorageState.DISABLED};
        var cache = new AggregateCache(switchable(state), new StorageMetrics(), 4096, 100, 4, new Inbox<>(8));
        cache.acceptChoices(List.of(choice(1L)));
        cache.acceptAgreements(List.of(agreement()));
        cache.acceptDifficulty(List.of(difficulty()));
        cache.acceptPatterns(List.of(pattern()));
        cache.onTick(1, 0);
        state[0] = StorageState.READY;
        assertTrue(cache.finalBatch().isEmpty(), "a disabled store keeps no row waiting");
    }

    @Test
    void everyRowListStaysUnderThePendingCapAndTheOverflowIsCounted() {
        var metrics = new StorageMetrics();
        var cache = new AggregateCache(accepting(), metrics, 4, 100, 4, new Inbox<>(8));
        var rows = new java.util.ArrayList<akyrion.thepredict.domain.distill.ChoiceStatDelta>();
        for (var index = 0; index < 10; index++) {
            rows.add(choice(index));
        }
        cache.acceptChoices(rows);
        assertEquals(6, metrics.total("AGGREGATE_KEY_DROPPED"));
        assertEquals(4, cache.finalBatch().orElseThrow().choices().size());
    }

    @Test
    void difficultyAndPatternRowsTravelInTheBatch() {
        var cache = new AggregateCache(accepting(), new StorageMetrics(), 4096, 1, 4, new Inbox<>(8));
        cache.acceptDifficulty(List.of(difficulty()));
        cache.acceptPatterns(List.of(pattern()));
        var sent = cache.flush(0, 1024).orElseThrow();
        assertEquals(1, sent.difficulty().size());
        assertEquals(1, sent.patterns().size());
        assertTrue(cache.finalBatch().isEmpty());
    }

    @Test
    void verdictsAreAskedEverySixtySecondsUntilALoadArrives() {
        var queries = new java.util.ArrayList<AggregateQuery>();
        var cache = new AggregateCache(recording(queries), new StorageMetrics(), 4096, 0, 4, new Inbox<>(8));
        var gates = new GateVerdicts();
        cache.useVerdicts(gates, 100);
        cache.onTick(0, 0);
        cache.onTick(600, 0);
        cache.onTick(GateVerdicts.REQUEST_EVERY_TICKS, 0);
        assertEquals(2, queries.size());
        assertEquals(new AggregateQuery.VerdictQuery(100), queries.getFirst());
        gates.load(List.of());
        cache.onTick(2 * GateVerdicts.REQUEST_EVERY_TICKS, 0);
        assertEquals(2, queries.size(), "a loaded gate asks no more");
        assertTrue(cache.requestDifficulty("guardian"));
        assertEquals(new AggregateQuery.DifficultyStatsQuery("guardian"), queries.getLast());
    }

    private static AggregateStore switchable(StorageState[] state) {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(state[0], "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                return true;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return true;
            }
        };
    }

    private static AggregateStore recording(List<AggregateQuery> queries) {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                return true;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return queries.add(query);
            }
        };
    }

    private static akyrion.thepredict.domain.distill.ChoiceStatDelta choice(long epoch) {
        return new akyrion.thepredict.domain.distill.ChoiceStatDelta(new akyrion.thepredict.domain.distill.ChoiceStatKey(
                "guardian", "r1:ALL", "ctx", "keep_distance", "jev", 1, "outcome"), 1, 1, epoch);
    }

    private static akyrion.thepredict.domain.distill.AgreementStatDelta agreement() {
        return new akyrion.thepredict.domain.distill.AgreementStatDelta("outcome", "guardian", "r1:ALL", "ctx", 0, 1,
                0.0, 0.0, 0, 0, 0, 0, 0L);
    }

    private static DifficultyStatDelta difficulty() {
        return new DifficultyStatDelta("guardian", "v1:player:1:NORMAL", 4, 1, 0.0, 0.0, 0, 0, 4, 1L, 1L, 1L);
    }

    private static PatternStatDelta pattern() {
        return new PatternStatDelta("guardian", "spams_clicks", "player", "observe", 1, 1, 0, 0, 0, 0.0, 0.0, 1L, 1L);
    }
}
