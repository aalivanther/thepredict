package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-01: a writer built for a release keeps no trace, and an opt-in trace keeps only its newest words (A5). */
class StorageWriterTraceBoundTest {
    private static final int EVENTS = 5000;

    @TempDir
    Path directory;

    @Test
    void aReleaseWriterKeepsNoTraceWhateverItWrites() throws Exception {
        var lines = new AtomicLong();
        var writer = new StorageWriter(spec("release", lines), new Inbox<InboxEntry>(8));
        offerAll(writer, lines);
        assertTrue(writer.trace().isEmpty(), "a release writer keeps no trace");
        assertTrue(writer.close(3000).finished());
    }

    @Test
    void anOptInTraceKeepsOnlyItsNewestWords() throws Exception {
        var lines = new AtomicLong();
        var writer = new StorageWriter(spec("traced", lines), new Inbox<InboxEntry>(8), WriterTrace.bounded(64));
        offerAll(writer, lines);
        var words = writer.trace();
        assertEquals(64, words.size());
        assertTrue(words.stream().allMatch("event"::equals), words.toString());
        assertTrue(writer.close(3000).finished());
        assertThrows(IllegalArgumentException.class, () -> WriterTrace.bounded(0));
        assertThrows(IllegalArgumentException.class, () -> WriterTrace.bounded(WriterTrace.MAX_WORDS + 1));
        assertTrue(WriterTrace.OFF.words().isEmpty());
    }

    private void offerAll(StorageWriter writer, AtomicLong lines) {
        for (var index = 0; index < EVENTS; index++) {
            assertTrue(writer.offer(new RecordEvent("planned", "20261006T120000Z-0a1b", 1L, 2L, index, "", Map.of())));
        }
        WriterHosts.until(() -> lines.get() >= EVENTS);
    }

    private StorageWriter.Spec spec(String name, AtomicLong lines) throws Exception {
        var records = directory.resolve(name);
        java.nio.file.Files.createDirectories(records);
        return WriterHosts.spec(records, directory.resolve(name + ".db"), true, false, 8192, file -> new Writer() {
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
        }, 8);
    }
}
