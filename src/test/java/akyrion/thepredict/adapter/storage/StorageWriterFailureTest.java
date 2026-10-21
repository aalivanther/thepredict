package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JS-06 and JS-24: a step that throws is caught at the frontier of the turn instead of ending the thread, and a failed
 * upkeep task, here the compression of each closed segment, is noted without failing the records.
 */
class StorageWriterFailureTest {
    private static final int EVENTS = 12;

    @TempDir
    Path directory;

    @Test
    void aStepThatThrowsIsCountedAndTheThreadKeepsTurning() throws Exception {
        var records = directory.resolve("throwing");
        Files.createDirectories(records);
        var writer = new StorageWriter(WriterHosts.spec(records, directory.resolve("throwing.db"), true, false, 8,
                file -> {
                    throw new IllegalStateException("opener");
                }, 8), new Inbox<InboxEntry>(8));
        assertTrue(writer.offer(event(0)));
        WriterHosts.until(() -> writer.internalFailures() >= 1);
        assertEquals("INTERNAL:IllegalStateException", writer.maintenanceCause());
        var before = writer.passes();
        WriterHosts.during(300_000_000L);
        assertTrue(writer.passes() > before, "the thread still turns after the failure");
        var stopped = new CountDownLatch(1);
        assertFalse(writer.close(200).finished(), "the event that cannot be written keeps the queue from draining");
        writer.whenStopped(stopped::countDown);
        assertTrue(stopped.await(3, TimeUnit.SECONDS), "a late writer still ends after the close");
    }

    @Test
    void aCompressionThatFailsOnEverySegmentLeavesTheEventsFlowing() throws Exception {
        var records = directory.resolve("compress");
        Files.createDirectories(records);
        var lines = new AtomicLong();
        var spec = new StorageWriter.Spec(records, directory.resolve("compress.db"), true, false, 64, 64L,
                60L * 60_000_000_000L, true, 14, 2_048L * 1024L * 1024L, "normal", 2000, 0, 7,
                "20261006T120000Z-0a1b", "0.0.1", "jev-1.0.0", "abc", file -> counting(lines), 8);
        var writer = new StorageWriter(spec, new Inbox<InboxEntry>(8));
        for (var index = 0; index < EVENTS; index++) {
            assertTrue(writer.offer(event(index)));
            var written = index + 1;
            WriterHosts.until(() -> lines.get() >= written);
        }
        WriterHosts.until(() -> "COMPRESS_FAILED".equals(writer.maintenanceCause()));
        assertEquals(EVENTS, lines.get());
        assertEquals(0, writer.dropped("failed"));
        assertEquals("", writer.eventsCause());
        assertTrue(writer.close(3000).finished());
    }

    private static RecordEvent event(int index) {
        return new RecordEvent("planned", "20261006T120000Z-0a1b", 1L, 2L, index, "", Map.of());
    }

    private static Writer counting(AtomicLong lines) {
        return new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) {
                for (var index = offset; index < offset + length; index++) {
                    if (buffer[index] == '\n') {
                        lines.incrementAndGet();
                    }
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }
}
