package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the writer does as it starts and while it is idle (JS-18, JS-19, JS-24, JS-32). */
class StorageWriterStartupTest {
    @TempDir
    Path directory;

    @Test
    void onlyTheNewestEarlierRunDecidesTheDirtyNotice() throws Exception {
        var database = directory.resolve("runs.db");
        var records = directory.resolve("runs-records");
        Files.createDirectories(records);
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, records.resolve("backups"));
        try (var connection = SqliteConnections.open(database, "normal", 2000);
             var statement = connection.createStatement()) {
            statement.execute("INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, "
                    + "config_sha256, sqlite_version) VALUES ('march', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')");
            statement.execute("INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, "
                    + "config_sha256, sqlite_version) VALUES ('april', 2, 1, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')");
        }
        var writer = new StorageWriter(WriterHosts.spec(records, database, false, true, 8, SegmentFiles.DISK, 8),
                new Inbox<InboxEntry>(8));
        WriterHosts.until(() -> writer.health().state() == StorageState.READY);
        assertEquals("", writer.health().cause());
        writer.close(3000);
    }

    @Test
    void anExpiredDayAndAStalePartialAreDeletedWhenTheWriterStarts() throws Exception {
        var records = directory.resolve("expired");
        var old = records.resolve("events").resolve("2000-01-01");
        Files.createDirectories(old);
        Files.writeString(old.resolve("events-run-0.jsonl"), "{}\n");
        var today = records.resolve("events").resolve(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString());
        Files.createDirectories(today);
        var partial = today.resolve("events-crashed-0.jsonl.gz.partial");
        Files.writeString(partial, "cut");
        var writer = new StorageWriter(WriterHosts.spec(records, directory.resolve("expired.db"), true, false, 8,
                SegmentFiles.DISK, 8), new Inbox<InboxEntry>(8));
        WriterHosts.until(() -> !Files.exists(old) && !Files.exists(partial));
        writer.close(3000);
    }

    @Test
    void aDedupBodyReachesItsFileWithoutAClose() throws Exception {
        var records = directory.resolve("bodies");
        var service = StorageService.open(WriterHosts.spec(records, directory.resolve("bodies.db"), true, false, 8,
                SegmentFiles.DISK, 8), RecordLevel.DECISIONS, RecordBodies.DEDUP, QuestionDetail.FULL,
                AnswerCacheMode.OFF, new Inbox<>(8), null, () -> {
        }, () -> 1L);
        service.tap().requestBody(new EncounterId(3), 1, "body-payload-flushed");
        WriterHosts.until(() -> bodies(records).contains("body-payload-flushed"));
        service.close(3000);
    }

    @Test
    void aTranslatedFullDiskMessageStillPausesInsteadOfFailingTheRecords() throws Exception {
        var records = directory.resolve("translated");
        Files.createDirectories(records);
        var writer = new StorageWriter(WriterHosts.spec(records, directory.resolve("translated.db"), true, false, 8,
                file -> failing("No queda espacio en el dispositivo"), 8), new Inbox<InboxEntry>(8));
        writer.useUsableSpace(() -> 0L);
        assertTrue(writer.offer(new RecordEvent("planned", "20261006T120000Z-0a1b", 1L, 2L, 3L, "", Map.of())));
        WriterHosts.until(writer::pausedSize);
        assertEquals("", writer.eventsCause());
        assertEquals(1, writer.diskNotices());
        assertEquals("DISK_FULL", writer.health().cause());
        writer.close(3000);
    }

    private static String bodies(Path records) {
        var root = records.resolve("events");
        if (!Files.isDirectory(root)) {
            return "";
        }
        try (var walk = Files.walk(root)) {
            var text = new StringBuilder();
            for (var file : walk.filter(path -> path.getFileName().toString().startsWith("bodies-")).toList()) {
                text.append(Files.readString(file));
            }
            return text.toString();
        } catch (IOException exception) {
            return "";
        }
    }

    private static Writer failing(String message) {
        return new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                throw new IOException(message);
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
