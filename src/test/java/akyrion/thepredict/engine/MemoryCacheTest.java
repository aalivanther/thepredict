package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemoryEntryState;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCacheTest {
    @Test
    void evictionPrefersADisconnectedPlayerAndATimeoutFailsOnce() {
        var counters = new MemoryCounters();
        var cache = new MemoryCache(2, counters);
        var online = new UUID(1, 1);
        var idle = new UUID(2, 2);
        var third = new UUID(3, 3);
        cache.loaded(online, new byte[] {1}, true, false, 1);
        cache.mark(online, true, true);
        cache.loaded(idle, new byte[] {2}, true, false, 2);
        cache.mark(idle, false, false);
        cache.loaded(third, new byte[] {3}, true, false, 3);
        assertEquals(2, cache.size());
        assertTrue(cache.slot(idle).isEmpty());
        assertEquals(0, counters.get(MemoryCounters.Kind.CACHE_EVICTED_ACTIVE));
        var loads = new MemoryCache(8, counters);
        var waiting = new UUID(4, 4);
        loads.beginLoad(waiting, 10);
        loads.timeout(waiting, 10 + 9_000_000_000L, 10);
        assertEquals(MemoryEntryState.PENDING, loads.slot(waiting).orElseThrow().state());
        loads.timeout(waiting, 10 + 11_000_000_000L, 10);
        assertEquals(MemoryEntryState.FAILED, loads.slot(waiting).orElseThrow().state());
        assertFalse(loads.beginLoad(waiting, 10 + 12_000_000_000L));
        assertTrue(loads.beginLoad(waiting, 10 + 11_000_000_000L + 60_000_000_000L));
        loads.loaded(new UUID(9, 9), new byte[] {1, 2}, true, true, 1);
        assertEquals(1, counters.get(MemoryCounters.Kind.RECORD_NEWER));
        assertEquals("memoria no cargada", loads.inspect(waiting));
    }

    @Test
    void theInspectTextsAreThoseOfMemoryInspectAndTheTimeoutOfTheSetMatchesOneByOne() {
        var counters = new MemoryCounters();
        var cache = new MemoryCache(8, counters);
        var absent = new UUID(5, 5);
        cache.loaded(absent, new byte[0], false, false, 1);
        assertEquals(MemoryInspect.emptyHistory(), cache.inspect(absent));
        assertEquals(MemoryInspect.notLoaded(), cache.inspect(new UUID(6, 6)));
        var first = new UUID(7, 7);
        var second = new UUID(8, 8);
        cache.beginLoad(first, 0L);
        cache.beginLoad(second, 5_000_000_000L);
        assertEquals(2, cache.pendingLoads());
        cache.timeoutPending(10_000_000_000L, 10);
        assertEquals(MemoryEntryState.FAILED, cache.slot(first).orElseThrow().state());
        assertEquals(MemoryEntryState.PENDING, cache.slot(second).orElseThrow().state(), "its timeout has not passed");
        assertEquals(1, cache.pendingLoads());
        cache.loaded(second, new byte[0], false, false, 11_000_000_000L);
        assertEquals(0, cache.pendingLoads(), "a load that arrived is no longer pending");
    }

    @Test
    void theViewsWithAndWithoutAStyleAgreeWithTheOptionalForm() {
        var cache = new MemoryCache(8, new MemoryCounters());
        var subject = new UUID(9, 1);
        var settings = akyrion.thepredict.domain.MemorySettings.DEFAULT;
        assertEquals(cache.view(subject, java.util.List.of(), java.util.List.of(), Optional.empty(), settings),
                cache.view(subject, java.util.List.of(), java.util.List.of(), settings));
    }
}
