package akyrion.thepredict.adapter.server.vocabulary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadWaveLogTest {
    @Test
    void aRepeatedMessageIsWrittenOncePerWave() {
        var lines = new ArrayList<String>();
        var clock = new AtomicLong(1_000L);
        var log = new LoadWaveLog(lines::add, clock::get);
        assertTrue(log.write("condition predictormove"));
        clock.addAndGet(LoadWaveLog.WAVE_GAP_NANOS);
        assertFalse(log.write("condition predictormove"));
        clock.addAndGet(LoadWaveLog.WAVE_GAP_NANOS + 1);
        assertTrue(log.write("condition predictormove"));
        assertEquals(List.of("condition predictormove", "condition predictormove"), lines);
    }

    @Test
    void eachWaveWritesAtMostTheLimit() {
        var lines = new ArrayList<String>();
        var clock = new AtomicLong();
        var log = new LoadWaveLog(lines::add, clock::get);
        for (var message = 0; message < LoadWaveLog.MAX_MESSAGES_PER_WAVE + 3; message++) {
            log.write("message " + message);
        }
        assertEquals(LoadWaveLog.MAX_MESSAGES_PER_WAVE, lines.size());
        clock.addAndGet(LoadWaveLog.WAVE_GAP_NANOS + 1);
        assertTrue(log.write("message " + (LoadWaveLog.MAX_MESSAGES_PER_WAVE + 1)));
    }

    @Test
    void concurrentWritersRespectTheLimit() throws Exception {
        var lines = new ConcurrentLinkedQueue<String>();
        var log = new LoadWaveLog(lines::add, () -> 0L);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<?>>();
            for (var thread = 0; thread < 8; thread++) {
                var prefix = "thread " + thread + " message ";
                futures.add(executor.submit(() -> {
                    start.await();
                    for (var message = 0; message < 50; message++) {
                        log.write(prefix + message);
                        log.write("shared " + message);
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
        assertEquals(LoadWaveLog.MAX_MESSAGES_PER_WAVE, lines.size());
        assertEquals(lines.size(), lines.stream().distinct().count());
    }
}
