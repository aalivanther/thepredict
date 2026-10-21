package akyrion.thepredict.adapter.server.duel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelWriterTest {
    @TempDir
    Path root;

    @Test
    void everyFileGetsItsHeaderAndTheSummaryIsWrittenLast() throws IOException {
        var folder = root.resolve("duels").resolve("local-20260929-120000");
        var writer = DuelWriter.start(folder);
        assertTrue(writer.offer(DuelFile.SAMPLES, "1,0,-,x"));
        assertTrue(writer.offer(DuelFile.DECISIONS, "{\"type\":\"local\"}"));
        writer.finish("# summary\n");
        assertTrue(writer.awaitStopped(10_000));
        assertEquals(DuelFile.values().length, Files.list(folder).count());
        assertEquals(DuelFile.SAMPLES.header().orElseThrow() + "\n1,0,-,x\n",
                Files.readString(folder.resolve("samples.csv")));
        assertEquals(DuelFile.ROUNDS.header().orElseThrow() + "\n", Files.readString(folder.resolve("rounds.csv")));
        assertEquals("{\"type\":\"local\"}\n", Files.readString(folder.resolve("decisions.jsonl")));
        assertEquals("# summary\n", Files.readString(folder.resolve("summary.md")));
        assertTrue(writer.failure().isEmpty());
        assertFalse(writer.offer(DuelFile.SAMPLES, "late"), "a finished writer drops rows");
        assertEquals(1, writer.dropped());
    }

    @Test
    void aSlowDiskNeverBlocksTheCallerAndAFullQueueDropsAndCounts() throws Exception {
        var release = new CountDownLatch(1);
        var writing = new CountDownLatch(1);
        var writer = DuelWriter.start(root.resolve("slow"), 4, DuelWriter.MAX_FILE_BYTES,
                DuelWriter.MAX_RECORDING_BYTES, file -> new Blocking(writing, release));
        assertTrue(writer.offer(DuelFile.SAMPLES, "first"));
        assertTrue(writing.await(10, TimeUnit.SECONDS), "the writer thread is stuck on the disk");
        for (var row = 0; row < 10; row++) {
            writer.offer(DuelFile.SAMPLES, "row " + row);
        }
        assertEquals(6, writer.dropped(), "4 queued, 6 dropped, and the caller never waited");
        release.countDown();
        writer.finish("");
        assertTrue(writer.awaitStopped(10_000));
        assertTrue(writer.failure().isEmpty());
    }

    @Test
    void aFileOrARecordingThatWouldPassItsLimitStopsTheWritingAndReportsIt() throws Exception {
        var perFile = DuelWriter.start(root.resolve("file"), 16, 60, 1_000, DuelWriterTest::memory);
        perFile.offer(DuelFile.CASTS, "x".repeat(40));
        perFile.finish("");
        assertTrue(perFile.awaitStopped(10_000));
        assertEquals(DuelWriter.FILE_LIMIT, perFile.limitReached().orElseThrow());
        assertEquals(1, perFile.skippedByLimit());
        var perRecording = DuelWriter.start(root.resolve("total"), 16, 10_000, 100, DuelWriterTest::memory);
        perRecording.offer(DuelFile.THREAT, "y".repeat(60));
        perRecording.finish("");
        assertTrue(perRecording.awaitStopped(10_000));
        assertEquals(DuelWriter.RECORDING_LIMIT, perRecording.limitReached().orElseThrow());
    }

    @Test
    void theWriterThreadIsADaemonWithItsName() throws Exception {
        var names = new StringBuilder();
        var writer = DuelWriter.start(root.resolve("named"), 4, 1_000, 10_000, file -> {
            names.append(Thread.currentThread().getName()).append(Thread.currentThread().isDaemon());
            return new StringWriter();
        });
        writer.offer(DuelFile.SAMPLES, "a");
        writer.finish("");
        assertTrue(writer.awaitStopped(10_000));
        assertTrue(names.toString().startsWith(DuelWriter.THREAD_NAME + "true"), names.toString());
        assertFalse(writer.running());
    }

    private static Writer memory(Path file) {
        return new StringWriter();
    }

    /** A disk that blocks on the first write until released. */
    private static final class Blocking extends Writer {
        private final CountDownLatch writing;
        private final CountDownLatch release;

        private Blocking(CountDownLatch writing, CountDownLatch release) {
            this.writing = writing;
            this.release = release;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            writing.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
