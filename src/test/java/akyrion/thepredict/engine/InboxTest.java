package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxTest {
    @Test
    void concurrentProducersAreDeliveredOnceOnlyWhenTheServerThreadDrains() throws Exception {
        var inbox = new Inbox<Integer>(64);
        var ready = new CountDownLatch(32);
        var release = new CountDownLatch(1);
        var accepted = new AtomicInteger();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<CompletableFuture<Void>>();
            for (var index = 0; index < 32; index++) {
                futures.add(CompletableFuture.runAsync(producer(inbox, index, ready, release, accepted), workers));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            assertEquals(0, inbox.drain(64, value -> {
                throw new AssertionError("an entry was delivered before its producer ran");
            }));
            release.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        var server = Thread.currentThread();
        var received = new HashSet<Integer>();
        assertEquals(32, accepted.get());
        assertEquals(32, inbox.drain(64, value -> {
            assertEquals(server, Thread.currentThread());
            assertTrue(received.add(value));
        }));
        assertEquals(0, inbox.drain(64, value -> {
            throw new AssertionError("an entry was delivered twice");
        }));
    }

    private static Runnable producer(Inbox<Integer> inbox, int value, CountDownLatch ready, CountDownLatch release,
                                     AtomicInteger accepted) {
        return () -> {
            ready.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            if (inbox.offer(value)) {
                accepted.incrementAndGet();
            }
        };
    }

    @Test
    void aFullInboxRejectsAndCountsWithoutThrowingAndNeverGrows() {
        var inbox = new Inbox<String>(3);
        assertTrue(inbox.offer("a"));
        assertTrue(inbox.offer("b"));
        assertTrue(inbox.offer("c"));
        assertFalse(inbox.offer("d"));
        assertFalse(inbox.offer("e"));
        assertEquals(2, inbox.rejected());
        assertEquals(3, inbox.size());
        assertEquals(3, inbox.capacity());
    }

    @Test
    void drainDeliversAtMostTheLimitInArrivalOrder() {
        var inbox = new Inbox<Integer>(10);
        for (var value = 0; value < 10; value++) {
            inbox.offer(value);
        }
        var delivered = new ArrayList<Integer>();
        assertEquals(4, inbox.drain(4, delivered::add));
        assertEquals(List.of(0, 1, 2, 3), delivered);
        assertEquals(6, inbox.size());
        assertEquals(0, inbox.drain(0, delivered::add));
        assertThrows(IllegalArgumentException.class, () -> inbox.drain(-1, delivered::add));
    }

    @Test
    void clearDropsTheWaitingEntriesAndTheCapacityMustBePositive() {
        var inbox = new Inbox<Integer>(2);
        inbox.offer(1);
        assertEquals(2, inbox.capacity(), "the configured capacity, whatever is waiting");
        inbox.offer(2);
        inbox.offer(3);
        assertEquals(2, inbox.capacity());
        assertTrue(inbox.offerStorage(new StorageReply.BatchApplied(1, 1L)));
        inbox.clear();
        assertEquals(0, inbox.size());
        assertEquals(1, inbox.drainStorage(4, reply -> {
        }), "the storage lane keeps its replies for its own drain");
        assertThrows(IllegalArgumentException.class, () -> new Inbox<Integer>(0));
        assertThrows(NullPointerException.class, () -> inbox.offer(null));
    }
}
