package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextSignalsTest {
    private static List<UUID> ids(int count) {
        var ids = new ArrayList<UUID>(count);
        for (var index = 0; index < count; index++) {
            ids.add(new UUID(7L, index));
        }
        return ids;
    }

    @Test
    void aPendingMobIsCoalescedAndNotCountedAsDropped() {
        var signals = new ContextSignals();
        var id = new UUID(1L, 1L);
        assertTrue(signals.offer(id));
        assertFalse(signals.offer(id));
        assertEquals(1, signals.pendingCount());
        assertEquals(0, signals.droppedCount());
    }

    @Test
    void drainDeliversInArrivalOrderAndRespectsMax() {
        var signals = new ContextSignals();
        var ids = ids(5);
        ids.forEach(signals::offer);
        var drained = new ArrayList<UUID>();
        assertEquals(2, signals.drain(drained::add, 2));
        assertEquals(ids.subList(0, 2), drained);
        assertEquals(3, signals.pendingCount());
        assertEquals(3, signals.drain(drained::add, 10));
        assertEquals(ids, drained);
        assertEquals(0, signals.drain(drained::add, 10));
    }

    @Test
    void aDrainedMobCanBeOfferedAgain() {
        var signals = new ContextSignals();
        var id = new UUID(1L, 2L);
        signals.offer(id);
        signals.drain(ignored -> { }, 1);
        assertTrue(signals.offer(id));
        assertEquals(1, signals.pendingCount());
    }

    @Test
    void drainOfZeroDeliversNothingAndNegativeMaxIsRejected() {
        var signals = new ContextSignals();
        signals.offer(new UUID(1L, 3L));
        assertEquals(0, signals.drain(ignored -> { }, 0));
        assertEquals(1, signals.pendingCount());
        assertThrows(IllegalArgumentException.class, () -> signals.drain(ignored -> { }, -1));
        assertThrows(NullPointerException.class, () -> signals.offer(null));
    }

    @Test
    void offersBeyondTheCapacityAreDroppedAndCounted() {
        var signals = new ContextSignals();
        var ids = ids(ContextSignals.CAPACITY + 1);
        for (var index = 0; index < ContextSignals.CAPACITY; index++) {
            assertTrue(signals.offer(ids.get(index)));
        }
        assertFalse(signals.offer(ids.get(ContextSignals.CAPACITY)));
        assertEquals(1, signals.droppedCount());
        assertFalse(signals.offer(ids.getFirst()));
        assertEquals(1, signals.droppedCount());
        assertEquals(ContextSignals.CAPACITY, signals.pendingCount());
        signals.drain(ignored -> { }, 1);
        assertTrue(signals.offer(ids.get(ContextSignals.CAPACITY)));
    }

    @Test
    void concurrentOffersNeverExceedTheCapacity() throws Exception {
        var signals = new ContextSignals();
        var producers = 8;
        var perProducer = 2_000;
        var accepted = new AtomicInteger();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(producers);
        try {
            var futures = new ArrayList<Future<?>>();
            for (var producer = 0; producer < producers; producer++) {
                var high = producer;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (var index = 0; index < perProducer; index++) {
                        if (signals.offer(new UUID(high, index))) {
                            accepted.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(ContextSignals.CAPACITY, accepted.get());
        assertEquals((long) producers * perProducer - ContextSignals.CAPACITY, signals.droppedCount());
        var drained = new HashSet<UUID>();
        assertEquals(ContextSignals.CAPACITY, signals.drain(drained::add, Integer.MAX_VALUE));
        assertEquals(ContextSignals.CAPACITY, drained.size());
    }

    @Test
    void concurrentProducersAndADrainingServerThreadLoseNoMob() throws Exception {
        var signals = new ContextSignals();
        var pool = ids(300);
        Set<UUID> delivered = ConcurrentHashMap.newKeySet();
        var start = new CountDownLatch(1);
        var producersDone = new CountDownLatch(4);
        var executor = Executors.newFixedThreadPool(5);
        try {
            var futures = new ArrayList<Future<?>>();
            for (var producer = 0; producer < 4; producer++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (var round = 0; round < 50; round++) {
                        pool.forEach(signals::offer);
                    }
                    producersDone.countDown();
                    return null;
                }));
            }
            var drainer = executor.submit(() -> {
                start.await();
                var maxPending = 0;
                while (producersDone.getCount() > 0) {
                    maxPending = Math.max(maxPending, signals.pendingCount());
                    signals.drain(delivered::add, 64);
                }
                return maxPending;
            });
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            assertTrue(drainer.get(30, TimeUnit.SECONDS) <= pool.size());
        } finally {
            executor.shutdownNow();
        }
        signals.drain(delivered::add, Integer.MAX_VALUE);
        assertEquals(new HashSet<>(pool), delivered);
        assertEquals(0, signals.pendingCount());
        assertEquals(0, signals.droppedCount());
    }
}
