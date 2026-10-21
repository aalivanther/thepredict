package akyrion.thepredict.adapter.server.zone;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneWorkerTest {
    /** SP-17: a job that dies with an error does not leave a dead daemon behind; the next offer runs. */
    @Test
    void anErrorInAJobDoesNotKillTheServiceForTheNextOffer() throws InterruptedException {
        var worker = new ZoneWorker(4);
        try {
            assertTrue(worker.offer(() -> {
                throw new AssertionError("scan");
            }));
            var died = worker.current();
            died.join(5000L);
            assertFalse(died.isAlive());
            var next = new CountDownLatch(1);
            assertTrue(worker.offer(next::countDown));
            assertTrue(next.await(2, TimeUnit.SECONDS));
        } finally {
            worker.close();
        }
    }

    @Test
    void theDaemonStopsWithinOneSecond() {
        var worker = new ZoneWorker(4);
        assertTrue(worker.offer(() -> { }));
        worker.close();
        assertFalse(worker.alive());
    }

    @Test
    void noThreadRunsBeforeTheFirstOffer() {
        var worker = new ZoneWorker(4);
        assertFalse(worker.alive());
        worker.close();
        assertFalse(worker.alive());
    }

    @Test
    void theDaemonRetiresWhenIdleAndTheNextOfferStartsItAgain() throws InterruptedException {
        var worker = new ZoneWorker(4);
        try {
            var first = new CountDownLatch(1);
            assertTrue(worker.offer(first::countDown));
            assertTrue(first.await(2, TimeUnit.SECONDS));
            var retired = worker.current();
            retired.join(5000L);
            assertFalse(retired.isAlive());
            assertFalse(worker.alive());
            var second = new CountDownLatch(1);
            assertTrue(worker.offer(second::countDown));
            assertTrue(second.await(2, TimeUnit.SECONDS));
        } finally {
            worker.close();
        }
        assertFalse(worker.alive());
    }
}
