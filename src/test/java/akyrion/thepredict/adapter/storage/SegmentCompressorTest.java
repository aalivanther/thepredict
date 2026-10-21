package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentCompressorTest {
    @TempDir
    Path directory;

    @Test
    void aFinishedGzipMatchesTheOriginalAndAMidFailureKeepsIt() throws Exception {
        var source = directory.resolve("events-run-0.jsonl");
        var bytes = "one\ntwo\n".getBytes(StandardCharsets.UTF_8);
        Files.write(source, bytes);
        SegmentCompressor.compress(source);
        assertTrue(Files.notExists(source));
        try (var gzip = new GZIPInputStream(Files.newInputStream(directory.resolve("events-run-0.jsonl.gz")))) {
            assertArrayEquals(bytes, gzip.readAllBytes());
        }
        var kept = directory.resolve("kept.jsonl");
        Files.write(kept, bytes);
        var broken = new OutputStream() {
            @Override
            public void write(int value) throws java.io.IOException {
                throw new java.io.IOException("mid");
            }

            @Override
            public void write(byte[] buffer, int offset, int length) throws java.io.IOException {
                throw new java.io.IOException("mid");
            }
        };
        var job = SegmentCompressor.start(kept, Files.newInputStream(kept), broken);
        assertThrows(java.io.IOException.class, () -> {
            while (!job.step()) {
                continue;
            }
        });
        assertArrayEquals(bytes, Files.readAllBytes(kept));
    }

    @Test
    void aFailedStepClosesBothStreams() throws Exception {
        var source = directory.resolve("events-run-1.jsonl");
        var bytes = randomBytes();
        Files.write(source, bytes);
        var inputClosed = new java.util.concurrent.atomic.AtomicBoolean();
        var outputClosed = new java.util.concurrent.atomic.AtomicBoolean();
        var input = new java.io.ByteArrayInputStream(bytes) {
            @Override
            public void close() {
                inputClosed.set(true);
            }
        };
        var job = SegmentCompressor.start(source, input, failingOutput(outputClosed));
        assertThrows(java.io.IOException.class, () -> {
            while (!job.step()) {
                continue;
            }
        });
        assertTrue(inputClosed.get() && outputClosed.get());
    }

    /** An output that fails on every write and notes its close. */
    private static OutputStream failingOutput(java.util.concurrent.atomic.AtomicBoolean closed) {
        return new OutputStream() {
            @Override
            public void write(int value) throws java.io.IOException {
                throw new java.io.IOException("mid");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
    }

    @Test
    void aStaleGzipIsReplacedAndALeftPartialIsSwept() throws Exception {
        var source = directory.resolve("events-run-2.jsonl");
        var bytes = randomBytes();
        Files.write(source, bytes);
        Files.writeString(directory.resolve("events-run-2.jsonl.gz"), "stale");
        SegmentCompressor.compress(source);
        try (var gzip = new GZIPInputStream(Files.newInputStream(directory.resolve("events-run-2.jsonl.gz")))) {
            assertArrayEquals(bytes, gzip.readAllBytes());
        }
        assertTrue(Files.notExists(directory.resolve("events-run-2.jsonl.gz.partial")));
        Files.writeString(directory.resolve("left.jsonl.gz.partial"), "cut");
        assertEquals(1, SegmentCompressor.deletePartials(directory));
    }

    /** Bytes that do not compress, so the stream writes while it runs and not only when it finishes. */
    private static byte[] randomBytes() {
        var bytes = new byte[256 * 1024];
        new java.util.Random(23).nextBytes(bytes);
        return bytes;
    }
}
